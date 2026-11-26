package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AiChatMessage
import com.example.data.repository.LinkoraAiRepository
import com.example.data.repository.LinkoraRepository
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.UUID

private const val AI_TAG = "LinkoraAiVM"

sealed interface AiStreamState {
    data object Idle : AiStreamState
    data object Thinking : AiStreamState
    data class Streaming(val partialText: String) : AiStreamState
    data class Error(val message: String, val canRetry: Boolean = true) : AiStreamState
}

/**
 * Supports BOTH Cloud Firestore persistence (when signed in with Google) and in-memory history
 * (in Developer Demo / Local Account mode), while always using real Firebase AI Logic (`Firebase.ai`)
 * with automatic multi-model failover so Linkora AI works smoothly everywhere.
 */
class LinkoraAiViewModel(
    private val repository: LinkoraRepository?,
    private val aiRepository: LinkoraAiRepository,
    private val currentUserId: String
) : ViewModel() {

    private val localMessages = MutableStateFlow<List<AiChatMessage>>(emptyList())

    val messages: StateFlow<List<AiChatMessage>> = if (repository != null) {
        repository.observeAiMessages(currentUserId)
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                Log.w(AI_TAG, "Error observing AI history", firestoreError ?: error)
                emit(emptyList())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())
    } else {
        localMessages.asStateFlow()
    }

    private val _streamState = MutableStateFlow<AiStreamState>(AiStreamState.Idle)
    val streamState: StateFlow<AiStreamState> = _streamState.asStateFlow()

    private var lastFailedPrompt: String? = null

    fun sendPrompt(rawPrompt: String, skipSavingUserMessage: Boolean = false) {
        val prompt = rawPrompt.trim()
        if (prompt.isBlank()) return
        if (_streamState.value is AiStreamState.Thinking || _streamState.value is AiStreamState.Streaming) {
            return
        }

        val priorHistory = messages.value
        lastFailedPrompt = null
        viewModelScope.launch {
            _streamState.value = AiStreamState.Thinking
            if (!skipSavingUserMessage) {
                appendMessage(role = "user", text = prompt)
            }

            val accumulated = StringBuilder()
            try {
                withTimeout(75_000L) {
                    aiRepository.streamReply(priorHistory, prompt).collect { chunk ->
                        accumulated.append(chunk)
                        if (accumulated.isNotEmpty()) {
                            _streamState.value = AiStreamState.Streaming(accumulated.toString())
                        }
                    }
                }
                val finalReply = accumulated.toString().trim()
                if (finalReply.isNotBlank()) {
                    appendMessage(role = "model", text = finalReply)
                    _streamState.value = AiStreamState.Idle
                } else {
                    lastFailedPrompt = prompt
                    _streamState.value = AiStreamState.Error(
                        "Linkora AI returned an empty response. Tap Retry to try again."
                    )
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(AI_TAG, "Linkora AI streaming timed out", e)
                lastFailedPrompt = prompt
                _streamState.value = AiStreamState.Error(
                    "Response took too long. Tap Retry to ask again."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(AI_TAG, "Linkora AI inference failed", e)
                lastFailedPrompt = prompt
                _streamState.value = AiStreamState.Error(
                    aiRepository.friendlyErrorMessage(e)
                )
            }
        }
    }

    fun retryLastPrompt() {
        val prompt = lastFailedPrompt ?: return
        sendPrompt(prompt, skipSavingUserMessage = true)
    }

    fun clearConversation() {
        viewModelScope.launch {
            if (repository != null) {
                repository.clearAiHistory()
            } else {
                localMessages.value = emptyList()
            }
            lastFailedPrompt = null
            _streamState.value = AiStreamState.Idle
        }
    }

    fun dismissError() {
        if (_streamState.value is AiStreamState.Error) {
            _streamState.value = AiStreamState.Idle
        }
    }

    private suspend fun appendMessage(role: String, text: String) {
        if (repository != null) {
            repository.saveAiMessage(role = role, text = text)
        } else {
            val item = AiChatMessage(
                id = UUID.randomUUID().toString(),
                role = role,
                text = text,
                createdAt = Timestamp.now()
            )
            localMessages.value = localMessages.value + item
        }
    }
}
