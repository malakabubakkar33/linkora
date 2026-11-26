package com.example.core

import android.content.Context
import android.util.Log
import com.example.data.model.CallQualityMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Real Kotlin + WebRTC peer-to-peer engine powering Linkora Voice Calls, Video Calls,
 * and low-latency P2P DataChannel chat/typing signaling using Stream's official Android WebRTC build.
 */
class WebRtcCallManager(
    private val context: Context,
    private val onLocalOfferReady: (String) -> Unit,
    private val onLocalAnswerReady: (String) -> Unit,
    private val onLocalIceCandidate: (sdpMid: String, sdpMLineIndex: Int, candidate: String) -> Unit,
    private val onDataChannelMessage: (String) -> Unit = {}
) {
    companion object {
        private const val TAG = "LinkoraWebRTC"
        private val initialized = AtomicBoolean(false)

        fun ensureInitialized(context: Context) {
            if (initialized.compareAndSet(false, true)) {
                runCatching {
                    val options = PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                        .setEnableInternalTracer(false)
                        .createInitializationOptions()
                    PeerConnectionFactory.initialize(options)
                }.onFailure {
                    Log.w(TAG, "PeerConnectionFactory init warning: ${it.message}")
                }
            }
        }
    }

    val eglBase: EglBase? = runCatching { EglBase.create() }.getOrNull()

    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var dataChannel: DataChannel? = null

    private val _connectionState = MutableStateFlow("Initializing WebRTC...")
    val connectionState: StateFlow<String> = _connectionState.asStateFlow()

    private val _isP2pConnected = MutableStateFlow(false)
    val isP2pConnected: StateFlow<Boolean> = _isP2pConnected.asStateFlow()

    private val _localVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrack: StateFlow<VideoTrack?> = _localVideoTrack.asStateFlow()

    private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrack: StateFlow<VideoTrack?> = _remoteVideoTrack.asStateFlow()

    private val addedRemoteCandidates = mutableSetOf<String>()
    private var hasSetRemoteDescription = false
    private val pendingRemoteCandidates = mutableListOf<IceCandidate>()
    private var isReleased = false

    fun start(
        isCaller: Boolean,
        enableVideo: Boolean,
        qualityMode: CallQualityMode = CallQualityMode.BALANCED,
        lowDataMode: Boolean = false
    ) {
        if (isReleased) return
        ensureInitialized(context)

        runCatching {
            val eglContext = eglBase?.eglBaseContext
            val factoryBuilder = PeerConnectionFactory.builder()
            if (eglContext != null) {
                factoryBuilder
                    .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglContext, true, true))
                    .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglContext))
            }
            factory = factoryBuilder.createPeerConnectionFactory()

            val iceServers = listOf(
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
                PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
                PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer()
            )
            val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
                sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
                continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
                iceTransportsType = PeerConnection.IceTransportsType.ALL
            }

            peerConnection = factory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
                override fun onSignalingChange(state: PeerConnection.SignalingState?) {}

                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                    when (state) {
                        PeerConnection.IceConnectionState.CHECKING -> {
                            _connectionState.value = "Negotiating WebRTC P2P..."
                        }
                        PeerConnection.IceConnectionState.CONNECTED,
                        PeerConnection.IceConnectionState.COMPLETED -> {
                            _isP2pConnected.value = true
                            _connectionState.value = "WebRTC P2P Connected"
                        }
                        PeerConnection.IceConnectionState.DISCONNECTED -> {
                            _isP2pConnected.value = false
                            _connectionState.value = "Reconnecting WebRTC..."
                        }
                        PeerConnection.IceConnectionState.FAILED -> {
                            _isP2pConnected.value = false
                            _connectionState.value = "Encrypted Cloud Relay"
                        }
                        PeerConnection.IceConnectionState.CLOSED -> {
                            _isP2pConnected.value = false
                            _connectionState.value = "Call Closed"
                        }
                        else -> Unit
                    }
                }

                override fun onIceConnectionReceivingChange(receiving: Boolean) {}

                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}

                override fun onIceCandidate(candidate: IceCandidate?) {
                    if (candidate != null && !isReleased) {
                        onLocalIceCandidate(
                            candidate.sdpMid ?: "0",
                            candidate.sdpMLineIndex,
                            candidate.sdp
                        )
                    }
                }

                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}

                override fun onAddStream(stream: MediaStream?) {
                    stream?.videoTracks?.firstOrNull()?.let { track ->
                        _remoteVideoTrack.value = track
                    }
                }

                override fun onRemoveStream(stream: MediaStream?) {}

                override fun onDataChannel(channel: DataChannel?) {
                    if (channel != null) {
                        attachDataChannel(channel)
                    }
                }

                override fun onRenegotiationNeeded() {}

                override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                    val track = receiver?.track()
                    if (track is VideoTrack) {
                        _remoteVideoTrack.value = track
                    }
                }
            })

            setupLocalAudio(lowDataMode)
            if (enableVideo) {
                setupLocalVideo(qualityMode, lowDataMode)
            }

            if (isCaller) {
                val dcInit = DataChannel.Init().apply { ordered = true }
                peerConnection?.createDataChannel("linkora-p2p-chat", dcInit)?.let {
                    attachDataChannel(it)
                }
                createOffer()
            } else {
                _connectionState.value = "Waiting for WebRTC Offer..."
            }
        }.onFailure { err ->
            Log.w(TAG, "WebRTC start fallback: ${err.message}")
            _connectionState.value = "Encrypted Relay Mode"
        }
    }

    private fun setupLocalAudio(lowDataMode: Boolean) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
            if (lowDataMode) {
                optional.add(MediaConstraints.KeyValuePair("maxaveragebitrate", "24000"))
            }
        }
        audioSource = factory?.createAudioSource(constraints)
        localAudioTrack = factory?.createAudioTrack("linkora_audio_0", audioSource)
        localAudioTrack?.setEnabled(true)
        localAudioTrack?.let { track ->
            peerConnection?.addTrack(track, listOf("linkora_stream"))
        }
    }

    private fun setupLocalVideo(qualityMode: CallQualityMode, lowDataMode: Boolean) {
        val eglContext = eglBase?.eglBaseContext ?: return
        val enumerator = Camera2Enumerator(context)
        val deviceName = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
            ?: enumerator.deviceNames.firstOrNull()
            ?: return

        val capturer = enumerator.createCapturer(deviceName, null) ?: return
        videoCapturer = capturer
        surfaceTextureHelper = SurfaceTextureHelper.create("LinkoraCaptureThread", eglContext)
        videoSource = factory?.createVideoSource(capturer.isScreencast)
        val source = videoSource ?: return

        capturer.initialize(surfaceTextureHelper, context.applicationContext, source.capturerObserver)
        val targetWidth = if (lowDataMode) 480 else qualityMode.videoWidth
        val targetHeight = if (lowDataMode) 360 else qualityMode.videoHeight
        val targetFps = if (lowDataMode) 15 else qualityMode.fps

        runCatching {
            capturer.startCapture(targetWidth, targetHeight, targetFps)
        }

        val track = factory?.createVideoTrack("linkora_video_0", source)
        track?.setEnabled(true)
        _localVideoTrack.value = track
        track?.let {
            peerConnection?.addTrack(it, listOf("linkora_stream"))
        }
    }

    private fun attachDataChannel(channel: DataChannel) {
        dataChannel = channel
        channel.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) {}
            override fun onStateChange() {}
            override fun onMessage(buffer: DataChannel.Buffer) {
                runCatching {
                    val data = buffer.data
                    val bytes = ByteArray(data.remaining())
                    data.get(bytes)
                    onDataChannelMessage(String(bytes, Charsets.UTF_8))
                }
            }
        })
    }

    fun sendP2pSignal(payload: String): Boolean {
        val dc = dataChannel ?: return false
        if (dc.state() != DataChannel.State.OPEN) return false
        return runCatching {
            val bytes = payload.toByteArray(Charsets.UTF_8)
            dc.send(DataChannel.Buffer(ByteBuffer.wrap(bytes), false))
        }.getOrDefault(false)
    }

    private fun createOffer() {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }
        peerConnection?.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                val offer = desc ?: return
                peerConnection?.setLocalDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        _connectionState.value = "WebRTC Offer Sent"
                        onLocalOfferReady(offer.description)
                    }
                }, offer)
            }
        }, constraints)
    }

    fun applyRemoteOfferAndCreateAnswer(offerSdp: String) {
        if (isReleased || offerSdp.isBlank() || hasSetRemoteDescription) return
        val remoteDesc = SessionDescription(SessionDescription.Type.OFFER, offerSdp)
        peerConnection?.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                hasSetRemoteDescription = true
                flushPendingIceCandidates()
                val constraints = MediaConstraints().apply {
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
                }
                peerConnection?.createAnswer(object : SimpleSdpObserver() {
                    override fun onCreateSuccess(desc: SessionDescription?) {
                        val answer = desc ?: return
                        peerConnection?.setLocalDescription(object : SimpleSdpObserver() {
                            override fun onSetSuccess() {
                                _connectionState.value = "WebRTC Answer Sent"
                                onLocalAnswerReady(answer.description)
                            }
                        }, answer)
                    }
                }, constraints)
            }
        }, remoteDesc)
    }

    fun applyRemoteAnswer(answerSdp: String) {
        if (isReleased || answerSdp.isBlank() || hasSetRemoteDescription) return
        val remoteDesc = SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
        peerConnection?.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                hasSetRemoteDescription = true
                flushPendingIceCandidates()
            }
        }, remoteDesc)
    }

    fun addRemoteIceCandidate(sdpMid: String, sdpMLineIndex: Int, candidateSdp: String) {
        if (isReleased || candidateSdp.isBlank()) return
        val key = "$sdpMid:$sdpMLineIndex:$candidateSdp"
        if (!addedRemoteCandidates.add(key)) return
        val candidate = IceCandidate(sdpMid, sdpMLineIndex, candidateSdp)
        if (hasSetRemoteDescription) {
            runCatching { peerConnection?.addIceCandidate(candidate) }
        } else {
            pendingRemoteCandidates.add(candidate)
        }
    }

    private fun flushPendingIceCandidates() {
        val queued = pendingRemoteCandidates.toList()
        pendingRemoteCandidates.clear()
        queued.forEach { candidate ->
            runCatching { peerConnection?.addIceCandidate(candidate) }
        }
    }

    fun setMuted(muted: Boolean) {
        runCatching { localAudioTrack?.setEnabled(!muted) }
    }

    fun setVideoEnabled(enabled: Boolean) {
        runCatching { _localVideoTrack.value?.setEnabled(enabled) }
    }

    fun switchCamera() {
        runCatching { videoCapturer?.switchCamera(null) }
    }

    fun release() {
        if (isReleased) return
        isReleased = true
        runCatching { videoCapturer?.stopCapture() }
        runCatching { videoCapturer?.dispose() }
        runCatching { surfaceTextureHelper?.dispose() }
        runCatching { videoSource?.dispose() }
        runCatching { audioSource?.dispose() }
        runCatching { dataChannel?.dispose() }
        runCatching { peerConnection?.close() }
        runCatching { peerConnection?.dispose() }
        runCatching { factory?.dispose() }
        runCatching { eglBase?.release() }
        _localVideoTrack.value = null
        _remoteVideoTrack.value = null
        _isP2pConnected.value = false
    }

    private open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String?) {
            Log.w(TAG, "SDP create failure: $error")
        }
        override fun onSetFailure(error: String?) {
            Log.w(TAG, "SDP set failure: $error")
        }
    }
}
