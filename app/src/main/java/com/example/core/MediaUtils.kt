package com.example.core

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

private const val MEDIA_TAG = "MediaUtils"

object ImageCodec {
    private val bitmapCache = LruCache<Int, Bitmap>(48)

    fun decodeBase64Bitmap(data: String?): Bitmap? {
        if (data.isNullOrBlank()) return null
        if (data.startsWith("http://") || data.startsWith("https://")) return null
        val key = data.hashCode()
        bitmapCache.get(key)?.let { return it }
        return try {
            val clean = if (data.contains(",")) data.substringAfter(",") else data
            val bytes = Base64.decode(clean, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.also {
                bitmapCache.put(key, it)
            }
        } catch (e: Exception) {
            Log.w(MEDIA_TAG, "Failed to decode base64 bitmap", e)
            null
        }
    }

    suspend fun compressUriToBase64(
        context: Context,
        uri: Uri,
        maxDimension: Int = 320,
        quality: Int = 72
    ): String? = withContext(Dispatchers.IO) {
        try {
            val input = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val original = input.use { BitmapFactory.decodeStream(it) } ?: return@withContext null
            val scaled = scaleBitmap(original, maxDimension)
            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            if (scaled != original) scaled.recycle()
            original.recycle()
            val encoded = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            "data:image/jpeg;base64,$encoded"
        } catch (e: Exception) {
            Log.e(MEDIA_TAG, "Image compression failed", e)
            null
        }
    }

    suspend fun extractVideoThumbnailBase64(
        context: Context,
        uri: Uri,
        maxDimension: Int = 420
    ): String? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val frame = retriever.getFrameAtTime(500_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
                ?: return@withContext null
            val scaled = scaleBitmap(frame, maxDimension)
            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 70, baos)
            if (scaled != frame) scaled.recycle()
            frame.recycle()
            val encoded = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            "data:image/jpeg;base64,$encoded"
        } catch (e: Exception) {
            Log.e(MEDIA_TAG, "Video thumbnail extraction failed", e)
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    fun compressBitmapToBase64(bitmap: Bitmap, maxDimension: Int = 180, quality: Int = 42): String {
        val scaled = scaleBitmap(bitmap, maxDimension)
        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        if (scaled != bitmap) scaled.recycle()
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    }

    private fun scaleBitmap(source: Bitmap, maxDim: Int): Bitmap {
        val width = source.width
        val height = source.height
        val largest = max(width, height)
        if (largest <= maxDim) return source
        val ratio = maxDim.toFloat() / largest.toFloat()
        val targetW = max(1, (width * ratio).toInt())
        val targetH = max(1, (height * ratio).toInt())
        return Bitmap.createScaledBitmap(source, targetW, targetH, true)
    }
}

class VoiceNoteRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startTimeMs: Long = 0L

    fun startRecording(): Boolean {
        stopAndDiscard()
        // Try high-quality AAC/MPEG_4 first, then fall back to 3GP/AMR_NB for maximum emulator/device compatibility
        if (tryStart(isAac = true)) return true
        return tryStart(isAac = false)
    }

    private fun tryStart(isAac: Boolean): Boolean {
        return try {
            val ext = if (isAac) "m4a" else "3gp"
            val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.$ext")
            outputFile = file
            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
            if (isAac) {
                mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                mr.setAudioEncodingBitRate(32_000)
                mr.setAudioSamplingRate(22_050)
            } else {
                mr.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                mr.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            }
            mr.setMaxDuration(60_000)
            mr.setOutputFile(file.absolutePath)
            mr.prepare()
            mr.start()
            recorder = mr
            startTimeMs = System.currentTimeMillis()
            true
        } catch (e: Exception) {
            Log.w(MEDIA_TAG, "Voice recorder start (aac=$isAac) failed: ${e.message}")
            stopAndDiscard()
            false
        }
    }

    fun stopAndEncode(): Pair<String, Int>? {
        val mr = recorder ?: return null
        val file = outputFile
        val elapsedMs = System.currentTimeMillis() - startTimeMs
        val elapsedSec = max(1, min(60, ((elapsedMs + 500L) / 1000L).toInt()))
        return try {
            if (elapsedMs < 350L) {
                // Prevent native MediaRecorder stop crash on ultra-short taps
                try {
                    Thread.sleep(350L - elapsedMs)
                } catch (_: InterruptedException) {
                }
            }
            mr.stop()
            mr.release()
            recorder = null
            if (file != null && file.exists() && file.length() in 64..320_000) {
                val bytes = file.readBytes()
                file.delete()
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                base64 to elapsedSec
            } else {
                Log.w(MEDIA_TAG, "Recorded voice file invalid size: ${file?.length()}")
                file?.delete()
                null
            }
        } catch (e: Exception) {
            Log.e(MEDIA_TAG, "Voice recorder stop failed", e)
            stopAndDiscard()
            null
        }
    }

    fun stopAndDiscard() {
        try {
            recorder?.release()
        } catch (_: Exception) {
        }
        recorder = null
        outputFile?.delete()
        outputFile = null
    }
}

class VoiceNotePlayer(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var playingId: String? = null

    fun playOrToggle(
        messageId: String,
        audioBase64: String,
        onStateChange: (playingMessageId: String?) -> Unit
    ) {
        if (playingId == messageId) {
            stop()
            onStateChange(null)
            return
        }
        stop()
        try {
            val bytes = Base64.decode(audioBase64, Base64.DEFAULT)
            val tempFile = File(context.cacheDir, "play_${messageId.hashCode()}.audio")
            FileOutputStream(tempFile).use { it.write(bytes) }
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(tempFile.absolutePath)
                setOnCompletionListener {
                    playingId = null
                    onStateChange(null)
                    tempFile.delete()
                }
                setOnErrorListener { _, _, _ ->
                    playingId = null
                    onStateChange(null)
                    tempFile.delete()
                    true
                }
                prepare()
                start()
            }
            mediaPlayer = player
            playingId = messageId
            onStateChange(messageId)
        } catch (e: Exception) {
            Log.e(MEDIA_TAG, "Voice playback failed", e)
            stop()
            onStateChange(null)
        }
    }

    fun stop() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
        playingId = null
    }
}

/**
 * Real-time low-bandwidth voice call engine using Android AudioRecord and AudioTrack.
 * Captures 8kHz 16-bit mono PCM bursts (~12KB/sec -> compact 8-bit mu-law-like scaled bytes)
 * and plays remote audio bursts smoothly during active calls.
 */
class LiveCallAudioEngine(private val context: Context) {
    private val sampleRate = 8000
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var captureJob: Job? = null
    @Volatile
    var muted: Boolean = false

    @SuppressLint("MissingPermission")
    fun start(
        scope: CoroutineScope,
        speakerOn: Boolean,
        onAudioChunkReady: suspend (String) -> Unit
    ) {
        stop()
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = speakerOn

            val minRecBuf = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)

            val recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minRecBuf * 2
            )

            val minPlayBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minPlayBuf * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioRecord = recorder
            audioTrack = track
            track.play()
            if (recorder.state == AudioRecord.STATE_INITIALIZED) {
                recorder.startRecording()
                captureJob = scope.launch(Dispatchers.IO) {
                    val frameSamples = 6400 // ~0.8s @ 8kHz
                    val shortBuf = ShortArray(frameSamples)
                    while (isActive) {
                        var offset = 0
                        while (offset < frameSamples && isActive) {
                            val read = recorder.read(shortBuf, offset, frameSamples - offset)
                            if (read <= 0) break
                            offset += read
                        }
                        if (!muted && offset > 0) {
                            // Downscale 16-bit PCM to signed 8-bit PCM for ultra-fast Firestore relay (~6.4KB)
                            val compact = ByteArray(offset)
                            var peak = 0
                            for (i in 0 until offset) {
                                val sample = shortBuf[i].toInt()
                                val absVal = if (sample < 0) -sample else sample
                                if (absVal > peak) peak = absVal
                                compact[i] = (sample shr 8).toByte()
                            }
                            if (peak > 350) {
                                val encoded = Base64.encodeToString(compact, Base64.NO_WRAP)
                                onAudioChunkReady(encoded)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(MEDIA_TAG, "Failed to start LiveCallAudioEngine", e)
        }
    }

    fun playRemoteChunk(base64Chunk: String) {
        if (base64Chunk.isBlank()) return
        try {
            val compact = Base64.decode(base64Chunk, Base64.DEFAULT)
            val pcm16 = ShortArray(compact.size)
            for (i in compact.indices) {
                pcm16[i] = (compact[i].toInt() shl 8).toShort()
            }
            audioTrack?.write(pcm16, 0, pcm16.size)
        } catch (e: Exception) {
            Log.w(MEDIA_TAG, "Failed to play remote audio chunk", e)
        }
    }

    fun setSpeakerphone(enabled: Boolean) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = enabled
        } catch (_: Exception) {
        }
    }

    fun stop() {
        captureJob?.cancel()
        captureJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {
        }
        audioRecord = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {
        }
        audioTrack = null
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.mode = AudioManager.MODE_NORMAL
        } catch (_: Exception) {
        }
    }
}

data class PickedFileAttachment(
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val base64Data: String?
)

object AttachmentCodec {
    private const val MAX_INLINE_BYTES = 240_000L // Fits safely inside Firestore 1MB doc limit after base64

    suspend fun readFileAttachment(
        context: Context,
        uri: Uri
    ): PickedFileAttachment? = withContext(Dispatchers.IO) {
        try {
            var fileName = "Attachment"
            var fileSize = 0L
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIdx >= 0) {
                        fileName = cursor.getString(nameIdx)?.take(180)?.ifBlank { "Attachment" } ?: "Attachment"
                    }
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                        fileSize = cursor.getLong(sizeIdx).coerceAtLeast(0L)
                    }
                }
            }
            val mimeType = context.contentResolver.getType(uri)?.take(120) ?: "application/octet-stream"
            val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                val baos = ByteArrayOutputStream()
                val buf = ByteArray(8192)
                var total = 0L
                while (true) {
                    val read = input.read(buf)
                    if (read <= 0) break
                    total += read
                    if (total <= MAX_INLINE_BYTES) {
                        baos.write(buf, 0, read)
                    } else {
                        break
                    }
                }
                if (fileSize <= 0L) fileSize = total
                if (total <= MAX_INLINE_BYTES) baos.toByteArray() else null
            }
            val encoded = bytes?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
            PickedFileAttachment(
                fileName = fileName,
                mimeType = mimeType,
                sizeBytes = fileSize,
                base64Data = encoded
            )
        } catch (e: Exception) {
            Log.e(MEDIA_TAG, "Failed to read file attachment", e)
            null
        }
    }

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024L -> "$bytes B"
            bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
            else -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }

    fun saveBase64ToCacheFile(
        context: Context,
        fileName: String,
        base64Data: String
    ): File? {
        return try {
            val clean = if (base64Data.contains(",")) base64Data.substringAfter(",") else base64Data
            val bytes = Base64.decode(clean, Base64.DEFAULT)
            val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(80).ifBlank { "file.bin" }
            val dir = File(context.cacheDir, "shared_attachments").apply { mkdirs() }
            val outFile = File(dir, "${System.currentTimeMillis()}_$safeName")
            FileOutputStream(outFile).use { it.write(bytes) }
            outFile
        } catch (e: Exception) {
            Log.e(MEDIA_TAG, "Failed to decode file attachment", e)
            null
        }
    }
}

data class ResolvedLocation(
    val latitude: Double,
    val longitude: Double,
    val label: String
)

object LocationHelper {
    @SuppressLint("MissingPermission")
    suspend fun fetchCurrentLocation(context: Context): ResolvedLocation? = withContext(Dispatchers.IO) {
        try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
                ?: return@withContext null
            val providers = listOf(
                android.location.LocationManager.GPS_PROVIDER,
                android.location.LocationManager.NETWORK_PROVIDER,
                android.location.LocationManager.PASSIVE_PROVIDER
            )
            var best: android.location.Location? = null
            for (provider in providers) {
                try {
                    if (!lm.isProviderEnabled(provider)) continue
                    val loc = lm.getLastKnownLocation(provider)
                    if (loc != null && (best == null || loc.accuracy < best.accuracy)) {
                        best = loc
                    }
                } catch (_: Exception) {
                }
            }
            if (best == null) return@withContext null
            val lat = best.latitude
            val lng = best.longitude
            val formatted = String.format(java.util.Locale.US, "%.5f, %.5f", lat, lng)
            var label = "Current Location ($formatted)"
            try {
                @Suppress("DEPRECATION")
                val addresses = android.location.Geocoder(context, java.util.Locale.getDefault())
                    .getFromLocation(lat, lng, 1)
                val addr = addresses?.firstOrNull()
                if (addr != null) {
                    val parts = listOfNotNull(addr.subLocality, addr.locality, addr.countryName)
                        .filter { it.isNotBlank() }
                    if (parts.isNotEmpty()) {
                        label = parts.joinToString(", ").take(160)
                    }
                }
            } catch (_: Exception) {
            }
            ResolvedLocation(latitude = lat, longitude = lng, label = label)
        } catch (e: Exception) {
            Log.e(MEDIA_TAG, "Location fetch failed", e)
            null
        }
    }
}

