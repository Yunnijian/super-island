package io.github.superisland.source.screenrecord

import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.view.Surface
import io.github.superisland.model.ScreenRecordingAudioSource
import io.github.superisland.model.ScreenRecordingConfig
import java.io.Closeable
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

internal data class ResolvedScreenRecordingDisplay(
    val width: Int,
    val height: Int,
    val densityDpi: Int,
    val framesPerSecond: Int,
)

/**
 * Immutable display facts collected by the foreground service immediately before capture starts.
 * They are deliberately separate from user configuration: callers cannot inject a display ID,
 * a virtual-display flag, or an arbitrary encoder format through this type.
 */
data class ScreenRecordingDisplay(
    val width: Int,
    val height: Int,
    val densityDpi: Int,
    val maximumFramesPerSecond: Int,
) {
    init {
        require(width > 0) { "Display width must be positive" }
        require(height > 0) { "Display height must be positive" }
        require(densityDpi > 0) { "Display density must be positive" }
        require(maximumFramesPerSecond > 0) { "Display frame rate must be positive" }
    }

    internal fun resolve(config: ScreenRecordingConfig): ResolvedScreenRecordingDisplay {
        val (scaledWidth, scaledHeight) = config.resolution.resolveDimensions(width, height)
        val (orientedWidth, orientedHeight) = config.orientation.applyTo(scaledWidth, scaledHeight)
        return ResolvedScreenRecordingDisplay(
            width = orientedWidth,
            height = orientedHeight,
            densityDpi = densityDpi,
            framesPerSecond = config.frameRate.resolveFramesPerSecond(maximumFramesPerSecond),
        )
    }
}

/** Result facts for the service to decide whether MediaStore/SAF output should be finalized. */
data class ScreenRecordingEncodingResult(
    val durationMillis: Long,
    val videoSampleCount: Long,
    val audioSampleCount: Long,
    val muxerStarted: Boolean,
)

/**
 * Standard MediaProjection recording core.
 *
 * The caller owns the consent flow and storage finalization. This class owns the supplied output
 * file descriptor while recording, closes it after the muxer releases, and uses only public
 * Android media APIs.
 */
class ScreenRecordingEncoder(
    private val mediaProjection: MediaProjection,
    private val config: ScreenRecordingConfig,
    private val output: ScreenRecordingOutput,
    private val display: ScreenRecordingDisplay,
    private val onProjectionStopped: (() -> Unit)? = null,
) : Closeable {
    enum class State {
        NEW,
        STARTING,
        RECORDING,
        STOPPING,
        STOPPED,
        FAILED,
    }

    private val lifecycleLock = Any()
    private val stopRequested = AtomicBoolean(false)
    private val projectionStopRequested = AtomicBoolean(false)
    private val outputDescriptorClosed = AtomicBoolean(false)
    private val firstFailure = AtomicReference<Throwable?>(null)
    private val videoSamples = AtomicLong(0)
    private val audioSamples = AtomicLong(0)
    private val workerExecutor: ExecutorService = Executors.newFixedThreadPool(2)
    private val controlExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val startupCompletion = CompletableFuture<Unit>()

    private var stateValue = State.NEW
    private var startCancellationRequested = false
    private var projectionStoppedDuringStart = false
    private var callbackRegistered = false
    private var termination: CompletableFuture<ScreenRecordingEncodingResult>? = null
    private var startedAtNanos = 0L

    private var videoPipeline: VideoPipeline? = null
    private var audioPipeline: AudioPipeline? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var muxer: MuxerCoordinator? = null
    private var videoWorker: Future<*>? = null
    private var audioWorker: Future<*>? = null

    private val projectionCallback =
        object : MediaProjection.Callback() {
            override fun onStop() {
                projectionStopRequested.set(true)
                runCatching { onProjectionStopped?.invoke() }

                val terminate =
                    synchronized(lifecycleLock) {
                        when (stateValue) {
                            State.STARTING -> {
                                projectionStoppedDuringStart = true
                                false
                            }

                            State.RECORDING -> true
                            else -> false
                        }
                    }
                if (terminate) scheduleTermination(stopProjection = false)
            }
        }

    val state: State
        get() = synchronized(lifecycleLock) { stateValue }

    val failure: Throwable?
        get() = firstFailure.get()

    /**
     * Creates the virtual display and starts the codec workers. MediaProjection consent must have
     * been granted by a foreground Activity before this method is called.
     */
    fun start() {
        synchronized(lifecycleLock) {
            check(stateValue == State.NEW) { "Screen recording encoder is already used" }
            stateValue = State.STARTING
        }

        try {
            val normalizedConfig = config.normalized()
            require(normalizedConfig == config) { "Screen recording config must be normalized" }
            require(!config.videoCodec.requiresHdrProfile) {
                "HDR recording needs a separately verified HDR capture pipeline"
            }

            val resolvedDisplay = display.resolve(config)
            // Anchor the muxer clock before any codec output so Surface PTS (often absolute /
            // boot-time based) never leaks into the MP4 duration when an audio track is present.
            startedAtNanos = System.nanoTime()
            val recordingEpochNanos = startedAtNanos
            val coordinator =
                MuxerCoordinator(
                    MediaMuxer(
                        output.fileDescriptor.fileDescriptor,
                        MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
                    ),
                    expectsAudio = config.audioSource != ScreenRecordingAudioSource.NONE,
                    presentationClockUs = {
                        ((System.nanoTime() - recordingEpochNanos) / 1_000L).coerceAtLeast(0L)
                    },
                    onVideoSample = { videoSamples.incrementAndGet() },
                    onAudioSample = { audioSamples.incrementAndGet() },
                )
            muxer = coordinator

            val video = createVideoPipeline(resolvedDisplay)
            videoPipeline = video

            val audio = createAudioPipelineIfNeeded()
            audioPipeline = audio

            mediaProjection.registerCallback(projectionCallback, null)
            callbackRegistered = true
            checkStartWasNotCancelled()

            virtualDisplay =
                mediaProjection.createVirtualDisplay(
                    VIRTUAL_DISPLAY_NAME,
                    resolvedDisplay.width,
                    resolvedDisplay.height,
                    resolvedDisplay.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    video.inputSurface,
                    null,
                    null,
                )

            synchronized(lifecycleLock) {
                check(!projectionStoppedDuringStart && !startCancellationRequested) {
                    "MediaProjection stopped before recording began"
                }
                audio?.capture?.start()
                stateValue = State.RECORDING
                videoWorker = workerExecutor.submit(::runVideoWorker)
                audioWorker = audio?.let { workerExecutor.submit(::runAudioWorker) }
            }
            startupCompletion.complete(Unit)
        } catch (failure: Throwable) {
            firstFailure.compareAndSet(null, failure)
            failStartCleanup()
            startupCompletion.completeExceptionally(failure)
            throw failure
        }
    }

    /** Stops input, drains EOS from active codecs, and returns output facts on success. */
    fun stop(): ScreenRecordingEncodingResult {
        val plan = beginTermination()
        if (plan.leader) finishTermination(plan.completion, stopProjection = true)
        return awaitCompletion(plan.completion)
    }

    override fun close() {
        while (true) {
            when (state) {
                State.NEW -> {
                    synchronized(lifecycleLock) {
                        if (stateValue != State.NEW) return@synchronized
                        stateValue = State.STOPPED
                    }
                    releaseResources(stopProjection = true)
                    shutdownExecutors()
                    return
                }

                State.STARTING -> {
                    if (requestStartCancellation()) {
                        requestProjectionStop()
                        awaitStartupCompletion()
                        return
                    }
                }

                State.RECORDING,
                State.STOPPING,
                -> {
                    runCatching { stop() }
                    return
                }

                State.STOPPED,
                State.FAILED,
                -> return
            }
        }
    }

    private fun createVideoPipeline(display: ResolvedScreenRecordingDisplay): VideoPipeline {
        val codec = MediaCodec.createEncoderByType(config.videoCodec.mimeType)
        try {
            val format =
                MediaFormat.createVideoFormat(config.videoCodec.mimeType, display.width, display.height).apply {
                    setInteger(
                        MediaFormat.KEY_COLOR_FORMAT,
                        MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface,
                    )
                    setInteger(
                        MediaFormat.KEY_BIT_RATE,
                        config.bitrate.resolveBitsPerSecond(
                            display.width,
                            display.height,
                            display.framesPerSecond,
                        ),
                    )
                    setInteger(MediaFormat.KEY_FRAME_RATE, display.framesPerSecond)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, VIDEO_I_FRAME_INTERVAL_SECONDS)
                }
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val inputSurface = codec.createInputSurface()
            codec.start()
            return VideoPipeline(codec, inputSurface)
        } catch (failure: Throwable) {
            releaseCodec(codec)
            throw failure
        }
    }

    private fun createAudioPipelineIfNeeded(): AudioPipeline? {
        if (config.audioSource == ScreenRecordingAudioSource.NONE) return null

        val capture = createAudioCapture(config.audioSource)
        var codec: MediaCodec? = null
        try {
            val activeCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            codec = activeCodec
            val format =
                MediaFormat.createAudioFormat(
                    MediaFormat.MIMETYPE_AUDIO_AAC,
                    AUDIO_SAMPLE_RATE,
                    AUDIO_CHANNEL_COUNT,
                ).apply {
                    setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                    setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BIT_RATE)
                    setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, AUDIO_READ_BUFFER_BYTES)
                }
            activeCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            activeCodec.start()
            return AudioPipeline(activeCodec, capture)
        } catch (failure: Throwable) {
            runCatching { capture.close() }
            codec?.let(::releaseCodec)
            throw failure
        }
    }

    private fun createAudioCapture(source: ScreenRecordingAudioSource): PcmCapture =
        when (source) {
            ScreenRecordingAudioSource.NONE -> error("No AudioRecord is needed for a silent recording")
            ScreenRecordingAudioSource.INTERNAL -> AudioRecordCapture(createPlaybackAudioRecord())
            ScreenRecordingAudioSource.MICROPHONE -> AudioRecordCapture(createMicrophoneAudioRecord())
            ScreenRecordingAudioSource.BOTH -> createMixedAudioCapture()
        }

    private fun createMixedAudioCapture(): PcmCapture {
        val playback = AudioRecordCapture(createPlaybackAudioRecord())
        try {
            return MixedPcmCapture(playback, AudioRecordCapture(createMicrophoneAudioRecord()))
        } catch (failure: Throwable) {
            runCatching { playback.close() }
            throw failure
        }
    }

    private fun createPlaybackAudioRecord(): AudioRecord {
        // Match IslandRecorder: only explicit playback-capture usages (not the microphone path).
        val configuration =
            AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()
        return buildAudioRecord(
            AudioRecord.Builder().setAudioPlaybackCaptureConfig(configuration),
        )
    }

    private fun createMicrophoneAudioRecord(): AudioRecord =
        buildAudioRecord(
            AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.MIC),
        )

    private fun buildAudioRecord(builder: AudioRecord.Builder): AudioRecord {
        val minimumBuffer =
            AudioRecord.getMinBufferSize(
                AUDIO_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
        require(minimumBuffer > 0) { "AudioRecord does not support the fixed capture format" }

        val record =
            builder
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(AUDIO_SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build(),
                )
                .setBufferSizeInBytes(maxOf(minimumBuffer * 2, AUDIO_READ_BUFFER_BYTES))
                .build()
        try {
            check(record.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord did not initialize" }
            return record
        } catch (failure: Throwable) {
            record.release()
            throw failure
        }
    }

    private fun runVideoWorker() {
        try {
            val video = requireNotNull(videoPipeline)
            while (true) {
                val reachedEndOfStream =
                    drainCodec(
                        codec = video.codec,
                        kind = MuxerTrack.VIDEO,
                        waitForEndOfStream = stopRequested.get(),
                    )
                if (reachedEndOfStream) {
                    if (!stopRequested.get()) {
                        throw IllegalStateException("Video encoder ended before recording was stopped")
                    }
                    return
                }
                // Yield so the audio worker can progress without starving surface input.
                if (!stopRequested.get()) {
                    Thread.yield()
                }
            }
        } catch (failure: Throwable) {
            recordWorkerFailure(failure)
        }
    }

    private fun runAudioWorker() {
        try {
            val audio = requireNotNull(audioPipeline)
            val pcm = ByteArray(AUDIO_READ_BUFFER_BYTES)
            var inputFrames = 0L
            try {
                while (!stopRequested.get()) {
                    val byteCount = audio.capture.read(pcm)
                    if (byteCount < 0) {
                        if (stopRequested.get()) break
                        throw IllegalStateException("AudioRecord read failed: $byteCount")
                    }
                    val alignedByteCount = byteCount - (byteCount % AUDIO_BYTES_PER_FRAME)
                    if (alignedByteCount == 0) {
                        waitForAudioInput()
                        continue
                    }

                    var offset = 0
                    while (offset < alignedByteCount && !stopRequested.get()) {
                        val queuedByteCount =
                            queueAudioInput(
                                codec = audio.codec,
                                pcm = pcm,
                                offset = offset,
                                byteCount = alignedByteCount - offset,
                                presentationTimeUs =
                                    inputFrames * MICROS_PER_SECOND / AUDIO_SAMPLE_RATE,
                            )
                        if (queuedByteCount == 0) break
                        offset += queuedByteCount
                        inputFrames += queuedByteCount / AUDIO_BYTES_PER_FRAME
                    }
                    if (
                        drainCodec(
                            codec = audio.codec,
                            kind = MuxerTrack.AUDIO,
                            waitForEndOfStream = false,
                        )
                    ) {
                        throw IllegalStateException("Audio encoder ended before recording was stopped")
                    }
                }
            } finally {
                queueAudioEndOfStream(
                    codec = audio.codec,
                    presentationTimeUs = inputFrames * MICROS_PER_SECOND / AUDIO_SAMPLE_RATE,
                )
                drainCodec(
                    codec = audio.codec,
                    kind = MuxerTrack.AUDIO,
                    waitForEndOfStream = true,
                )
            }
        } catch (failure: Throwable) {
            recordWorkerFailure(failure)
        }
    }

    private fun queueAudioInput(
        codec: MediaCodec,
        pcm: ByteArray,
        offset: Int,
        byteCount: Int,
        presentationTimeUs: Long,
    ): Int {
        while (!stopRequested.get()) {
            val inputIndex = codec.dequeueInputBuffer(CODEC_DEQUEUE_TIMEOUT_US)
            if (inputIndex < 0) {
                drainCodec(codec, MuxerTrack.AUDIO, waitForEndOfStream = false)
                continue
            }

            val inputBuffer = requireNotNull(codec.getInputBuffer(inputIndex))
            inputBuffer.clear()
            val queuedByteCount =
                minOf(byteCount, inputBuffer.remaining()) -
                    (minOf(byteCount, inputBuffer.remaining()) % AUDIO_BYTES_PER_FRAME)
            check(queuedByteCount > 0) { "AAC encoder input buffer is too small for PCM16" }
            inputBuffer.put(pcm, offset, queuedByteCount)
            codec.queueInputBuffer(inputIndex, 0, queuedByteCount, presentationTimeUs, 0)
            return queuedByteCount
        }
        return 0
    }

    private fun queueAudioEndOfStream(
        codec: MediaCodec,
        presentationTimeUs: Long,
    ) {
        val deadlineNanos = System.nanoTime() + AUDIO_EOS_TIMEOUT_MILLIS * NANOS_PER_MILLISECOND
        while (true) {
            val inputIndex = codec.dequeueInputBuffer(CODEC_DEQUEUE_TIMEOUT_US)
            if (inputIndex >= 0) {
                codec.queueInputBuffer(
                    inputIndex,
                    0,
                    0,
                    presentationTimeUs,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                )
                return
            }
            if (System.nanoTime() >= deadlineNanos) {
                throw TimeoutException("Timed out while queuing audio EOS")
            }
        }
    }

    private fun waitForAudioInput() {
        try {
            Thread.sleep(AUDIO_IDLE_BACKOFF_MILLIS)
        } catch (interrupted: InterruptedException) {
            if (!stopRequested.get()) throw interrupted
            Thread.currentThread().interrupt()
        }
    }

    @Suppress("DEPRECATION")
    private fun drainCodec(
        codec: MediaCodec,
        kind: MuxerTrack,
        waitForEndOfStream: Boolean,
    ): Boolean {
        val bufferInfo = MediaCodec.BufferInfo()
        val deadlineNanos =
            if (waitForEndOfStream) {
                System.nanoTime() + CODEC_EOS_TIMEOUT_MILLIS * NANOS_PER_MILLISECOND
            } else {
                Long.MAX_VALUE
            }

        while (true) {
            when (val outputIndex = codec.dequeueOutputBuffer(bufferInfo, CODEC_DEQUEUE_TIMEOUT_US)) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!waitForEndOfStream) return false
                    if (System.nanoTime() >= deadlineNanos) {
                        throw TimeoutException("Timed out while draining ${kind.name.lowercase()} EOS")
                    }
                }

                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    requireNotNull(muxer).registerFormat(kind, codec.outputFormat)
                }

                MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit

                else -> {
                    if (outputIndex < 0) {
                        throw IllegalStateException("Unexpected MediaCodec output status: $outputIndex")
                    }
                    // Copy + release the codec buffer BEFORE muxer.writeSample. Holding the buffer
                    // across a contended muxer lock backs up the Surface input and freezes video
                    // while audio still advances (common when any audio track is enabled).
                    val sampleCopy: ByteArray?
                    val sampleInfo: MediaCodec.BufferInfo?
                    try {
                        if (
                            bufferInfo.size > 0 &&
                                bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0
                        ) {
                            val outputBuffer = requireNotNull(codec.getOutputBuffer(outputIndex))
                            val duplicate = outputBuffer.duplicate()
                            duplicate.position(bufferInfo.offset)
                            duplicate.limit(bufferInfo.offset + bufferInfo.size)
                            val bytes = ByteArray(bufferInfo.size)
                            duplicate.get(bytes)
                            sampleCopy = bytes
                            sampleInfo =
                                MediaCodec.BufferInfo().apply {
                                    set(0, bytes.size, bufferInfo.presentationTimeUs, bufferInfo.flags)
                                }
                        } else {
                            sampleCopy = null
                            sampleInfo = null
                        }
                    } finally {
                        codec.releaseOutputBuffer(outputIndex, false)
                    }
                    if (sampleCopy != null && sampleInfo != null) {
                        requireNotNull(muxer).writeSample(kind, ByteBuffer.wrap(sampleCopy), sampleInfo)
                    }
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return true
                }
            }
        }
    }

    private fun scheduleTermination(stopProjection: Boolean) {
        try {
            controlExecutor.execute {
                val plan = beginTermination()
                if (plan.leader) finishTermination(plan.completion, stopProjection = stopProjection)
            }
        } catch (_: RejectedExecutionException) {
            // The encoder has already reached a terminal state.
        }
    }

    private fun beginTermination(): TerminationPlan {
        synchronized(lifecycleLock) {
            val existing = termination
            if (existing != null) return TerminationPlan(existing, leader = false)
            check(stateValue == State.RECORDING) { "Screen recording encoder is not recording" }
            val completion = CompletableFuture<ScreenRecordingEncodingResult>()
            termination = completion
            stateValue = State.STOPPING
            return TerminationPlan(completion, leader = true)
        }
    }

    private fun finishTermination(
        completion: CompletableFuture<ScreenRecordingEncodingResult>,
        stopProjection: Boolean,
    ) {
        var terminalFailure: Throwable? = firstFailure.get()
        var workersTerminated = false
        try {
            requestWorkerStop()
            if (stopProjection) requestProjectionStop()
            awaitWorker(videoWorker)
            awaitWorker(audioWorker)
            workersTerminated = awaitWorkerExecutorShutdown()
        } catch (failure: Throwable) {
            terminalFailure = combineFailures(terminalFailure, failure)
            workersTerminated = abortWorkerExecutorShutdown()
        } finally {
            if (workersTerminated) {
                terminalFailure = combineFailures(terminalFailure, releaseResources(stopProjection = false))
            } else {
                terminalFailure =
                    combineFailures(
                        terminalFailure,
                        IllegalStateException("Recording workers did not terminate; media resources were retained"),
                    )
            }
            terminalFailure = combineFailures(terminalFailure, firstFailure.get())
            controlExecutor.shutdown()
        }

        val result =
            ScreenRecordingEncodingResult(
                durationMillis =
                    ((System.nanoTime() - startedAtNanos) / NANOS_PER_MILLISECOND).coerceAtLeast(0L),
                videoSampleCount = videoSamples.get(),
                audioSampleCount = audioSamples.get(),
                muxerStarted = muxerStartedAtTermination,
            )
        synchronized(lifecycleLock) {
            stateValue = if (terminalFailure == null) State.STOPPED else State.FAILED
        }
        if (terminalFailure == null) {
            completion.complete(result)
        } else {
            firstFailure.compareAndSet(null, terminalFailure)
            completion.completeExceptionally(terminalFailure)
        }
    }

    private var muxerStartedAtTermination = false

    private fun requestWorkerStop() {
        stopRequested.set(true)
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { audioPipeline?.capture?.stop() }
        runCatching { videoPipeline?.codec?.signalEndOfInputStream() }
    }

    private fun awaitWorker(worker: Future<*>?) {
        worker ?: return
        try {
            worker.get(WORKER_STOP_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        } catch (failure: ExecutionException) {
            throw failure.cause ?: failure
        }
    }

    private fun awaitWorkerExecutorShutdown(): Boolean {
        workerExecutor.shutdown()
        if (awaitWorkerExecutorTermination()) {
            return true
        }
        workerExecutor.shutdownNow()
        return awaitWorkerExecutorTermination()
    }

    private fun abortWorkerExecutorShutdown(): Boolean {
        workerExecutor.shutdownNow()
        return awaitWorkerExecutorTermination()
    }

    private fun awaitWorkerExecutorTermination(): Boolean =
        try {
            workerExecutor.awaitTermination(WORKER_EXECUTOR_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }

    private fun awaitCompletion(
        completion: CompletableFuture<ScreenRecordingEncodingResult>,
    ): ScreenRecordingEncodingResult =
        try {
            completion.get(COMPLETION_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        } catch (failure: ExecutionException) {
            val cause = failure.cause
            if (cause is RuntimeException) throw cause
            throw IllegalStateException("Screen recording stop failed", cause ?: failure)
        } catch (failure: TimeoutException) {
            throw IllegalStateException("Timed out while stopping screen recording", failure)
        }

    private fun recordWorkerFailure(failure: Throwable) {
        if (firstFailure.compareAndSet(null, failure)) {
            scheduleTermination(stopProjection = true)
        }
    }

    private fun checkStartWasNotCancelled() {
        synchronized(lifecycleLock) {
            check(!projectionStoppedDuringStart && !startCancellationRequested) {
                "MediaProjection stopped before recording began"
            }
        }
    }

    private fun requestStartCancellation(): Boolean =
        synchronized(lifecycleLock) {
            if (stateValue != State.STARTING) return@synchronized false
            startCancellationRequested = true
            projectionStoppedDuringStart = true
            true
        }

    private fun requestProjectionStop() {
        if (projectionStopRequested.compareAndSet(false, true)) {
            runCatching { mediaProjection.stop() }
        }
    }

    private fun awaitStartupCompletion() {
        runCatching { startupCompletion.get() }
    }

    private fun failStartCleanup() {
        stopRequested.set(true)
        synchronized(lifecycleLock) {
            stateValue = State.FAILED
        }
        releaseResources(stopProjection = true)
        shutdownExecutors()
    }

    private fun releaseResources(stopProjection: Boolean): Throwable? {
        var failure: Throwable? = null
        failure = releaseSafely(failure) { virtualDisplay?.release() }
        virtualDisplay = null
        if (callbackRegistered) {
            failure = releaseSafely(failure) { mediaProjection.unregisterCallback(projectionCallback) }
            callbackRegistered = false
        }
        failure = releaseSafely(failure) { audioPipeline?.capture?.close() }
        val audio = audioPipeline
        audioPipeline = null
        failure = releaseSafely(failure) { audio?.codec?.let(::releaseCodec) }
        val video = videoPipeline
        videoPipeline = null
        failure = releaseSafely(failure) { video?.inputSurface?.release() }
        failure = releaseSafely(failure) { video?.codec?.let(::releaseCodec) }
        val coordinator = muxer
        muxerStartedAtTermination = coordinator?.started ?: muxerStartedAtTermination
        muxer = null
        failure = releaseSafely(failure) { coordinator?.close() }
        if (stopProjection && projectionStopRequested.compareAndSet(false, true)) {
            failure = releaseSafely(failure) { mediaProjection.stop() }
        }
        if (outputDescriptorClosed.compareAndSet(false, true)) {
            failure = releaseSafely(failure) { output.fileDescriptor.close() }
        }
        return failure
    }

    private fun shutdownExecutors() {
        workerExecutor.shutdownNow()
        controlExecutor.shutdown()
    }

    private data class VideoPipeline(
        val codec: MediaCodec,
        val inputSurface: Surface,
    )

    private data class AudioPipeline(
        val codec: MediaCodec,
        val capture: PcmCapture,
    )

    private interface PcmCapture : Closeable {
        fun start()

        fun read(destination: ByteArray): Int

        fun stop()
    }

    private class AudioRecordCapture(private val record: AudioRecord) : PcmCapture {
        override fun start() {
            record.startRecording()
            check(record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                "AudioRecord did not enter recording state"
            }
        }

        override fun read(destination: ByteArray): Int =
            record.read(destination, 0, destination.size, AudioRecord.READ_NON_BLOCKING)

        override fun stop() {
            runCatching { record.stop() }
        }

        override fun close() {
            stop()
            record.release()
        }
    }

    private class MixedPcmCapture(
        private val playback: PcmCapture,
        private val microphone: PcmCapture,
    ) : PcmCapture {
        private val playbackBuffer = ByteArray(AUDIO_READ_BUFFER_BYTES)
        private val microphoneBuffer = ByteArray(AUDIO_READ_BUFFER_BYTES)
        private var playbackBytes = 0
        private var microphoneBytes = 0

        override fun start() {
            try {
                playback.start()
                microphone.start()
            } catch (failure: Throwable) {
                stop()
                throw failure
            }
        }

        override fun read(destination: ByteArray): Int {
            val playbackRead = fillPlaybackBufferIfNeeded()
            if (playbackRead < 0) return playbackRead
            val microphoneRead = fillMicrophoneBufferIfNeeded()
            if (microphoneRead < 0) return microphoneRead
            if (playbackBytes == 0 && microphoneBytes == 0) return 0

            val byteCount =
                minOf(
                    destination.size,
                    when {
                        playbackBytes == 0 -> microphoneBytes
                        microphoneBytes == 0 -> playbackBytes
                        else -> minOf(playbackBytes, microphoneBytes)
                    },
                )
            val alignedByteCount = byteCount - (byteCount % AUDIO_BYTES_PER_FRAME)
            var offset = 0
            while (offset < alignedByteCount) {
                val mixed =
                    (
                        if (offset < playbackBytes) readPcm16(playbackBuffer, offset) else 0
                    )
                        .plus(
                            if (offset < microphoneBytes) readPcm16(microphoneBuffer, offset) else 0,
                        )
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                writePcm16(destination, offset, mixed)
                offset += AUDIO_BYTES_PER_FRAME
            }
            if (playbackBytes > 0) playbackBytes = consume(playbackBuffer, playbackBytes, alignedByteCount)
            if (microphoneBytes > 0) {
                microphoneBytes = consume(microphoneBuffer, microphoneBytes, alignedByteCount)
            }
            return alignedByteCount
        }

        private fun fillPlaybackBufferIfNeeded(): Int {
            if (playbackBytes > 0) return 0
            val read = playback.read(playbackBuffer)
            if (read < 0) return read
            playbackBytes = read - (read % AUDIO_BYTES_PER_FRAME)
            return 0
        }

        private fun fillMicrophoneBufferIfNeeded(): Int {
            if (microphoneBytes > 0) return 0
            val read = microphone.read(microphoneBuffer)
            if (read < 0) return read
            microphoneBytes = read - (read % AUDIO_BYTES_PER_FRAME)
            return 0
        }

        private fun consume(
            buffer: ByteArray,
            availableBytes: Int,
            consumedBytes: Int,
        ): Int {
            val remaining = availableBytes - consumedBytes
            if (remaining > 0) System.arraycopy(buffer, consumedBytes, buffer, 0, remaining)
            return remaining
        }

        override fun stop() {
            playback.stop()
            microphone.stop()
        }

        override fun close() {
            playback.close()
            microphone.close()
        }
    }

    private enum class MuxerTrack {
        VIDEO,
        AUDIO,
    }

    private class MuxerCoordinator(
        private val muxer: MediaMuxer,
        private val expectsAudio: Boolean,
        /**
         * Monotonic presentation clock in microseconds, relative to recording start.
         * Surface/video MediaCodec timestamps on HyperOS are often absolute and would make the
         * container report multi-hour durations for a few seconds of capture once an audio track
         * is interleaved.
         */
        private val presentationClockUs: () -> Long,
        private val onVideoSample: () -> Unit,
        private val onAudioSample: () -> Unit,
    ) : Closeable {
        private val lock = Any()
        private val pendingSamples = ArrayList<PendingSample>()
        private var pendingBytes = 0L
        private var videoFormat: MediaFormat? = null
        private var audioFormat: MediaFormat? = null
        private var videoTrackIndex = -1
        private var audioTrackIndex = -1
        private var released = false
        private var lastVideoPtsUs = -1L
        private var lastAudioPtsUs = -1L

        var started = false
            private set

        fun registerFormat(
            kind: MuxerTrack,
            format: MediaFormat,
        ) {
            synchronized(lock) {
                check(!released) { "MediaMuxer is released" }
                when (kind) {
                    MuxerTrack.VIDEO -> {
                        check(videoFormat == null) { "Video output format changed twice" }
                        videoFormat = format
                    }

                    MuxerTrack.AUDIO -> {
                        check(audioFormat == null) { "Audio output format changed twice" }
                        audioFormat = format
                    }
                }
                startIfReadyLocked()
            }
        }

        fun writeSample(
            kind: MuxerTrack,
            buffer: ByteBuffer,
            bufferInfo: MediaCodec.BufferInfo,
        ) {
            synchronized(lock) {
                check(!released) { "MediaMuxer is released" }
                if (!started) {
                    val copied = copySample(kind, buffer, bufferInfo)
                    check(pendingBytes + copied.bytes.size <= MAX_PENDING_MUXER_BYTES) {
                        "Audio/video formats did not become ready in time"
                    }
                    pendingSamples += PendingSample(kind, copied.bytes, copied.presentationTimeUs, copied.flags)
                    pendingBytes += copied.bytes.size
                    return
                }
                writeSampleLocked(kind, buffer, bufferInfo)
            }
        }

        override fun close() {
            synchronized(lock) {
                if (released) return
                released = true
                var failure: Throwable? = null
                if (started) {
                    try {
                        muxer.stop()
                    } catch (stopFailure: Throwable) {
                        failure = stopFailure
                    }
                }
                try {
                    muxer.release()
                } catch (releaseFailure: Throwable) {
                    failure = combineFailures(failure, releaseFailure)
                }
                pendingSamples.clear()
                pendingBytes = 0L
                failure?.let { throw it }
            }
        }

        private fun startIfReadyLocked() {
            if (started || videoFormat == null || expectsAudio && audioFormat == null) return

            videoTrackIndex = muxer.addTrack(requireNotNull(videoFormat))
            if (expectsAudio) audioTrackIndex = muxer.addTrack(requireNotNull(audioFormat))
            muxer.start()
            started = true
            pendingSamples.forEach { pending ->
                val info = MediaCodec.BufferInfo().apply {
                    set(0, pending.bytes.size, pending.presentationTimeUs, pending.flags)
                }
                writeSampleLocked(pending.kind, ByteBuffer.wrap(pending.bytes), info)
            }
            pendingSamples.clear()
            pendingBytes = 0L
        }

        private fun writeSampleLocked(
            kind: MuxerTrack,
            buffer: ByteBuffer,
            bufferInfo: MediaCodec.BufferInfo,
        ) {
            val track = trackIndexFor(kind)
            val presentationTimeUs = normalizePresentationTimeUs(kind, bufferInfo.presentationTimeUs)
            val adjustedInfo =
                MediaCodec.BufferInfo().apply {
                    set(bufferInfo.offset, bufferInfo.size, presentationTimeUs, bufferInfo.flags)
                }
            val sample =
                buffer
                    .duplicate()
                    .apply {
                        position(adjustedInfo.offset)
                        limit(adjustedInfo.offset + adjustedInfo.size)
                    }
            muxer.writeSampleData(track, sample, adjustedInfo)
            when (kind) {
                MuxerTrack.VIDEO -> onVideoSample()
                MuxerTrack.AUDIO -> onAudioSample()
            }
        }

        /**
         * Video: ignore Surface/codec PTS (often absolute boot-time on HyperOS with audio).
         * Audio: keep the PCM-frame clock (already relative to 0) but force monotonicity.
         */
        private fun normalizePresentationTimeUs(
            kind: MuxerTrack,
            rawPtsUs: Long,
        ): Long =
            when (kind) {
                MuxerTrack.VIDEO -> {
                    val clock = presentationClockUs()
                    val next =
                        if (lastVideoPtsUs < 0L) {
                            clock
                        } else {
                            maxOf(clock, lastVideoPtsUs + 1)
                        }
                    lastVideoPtsUs = next
                    next
                }
                MuxerTrack.AUDIO -> {
                    val preferred = rawPtsUs.coerceAtLeast(0L)
                    val next =
                        if (lastAudioPtsUs < 0L) {
                            preferred
                        } else if (preferred > lastAudioPtsUs) {
                            preferred
                        } else {
                            lastAudioPtsUs + 1
                        }
                    lastAudioPtsUs = next
                    next
                }
            }

        private fun trackIndexFor(kind: MuxerTrack): Int =
            when (kind) {
                MuxerTrack.VIDEO -> check(videoTrackIndex >= 0) { "Video track is not ready" }.let { videoTrackIndex }
                MuxerTrack.AUDIO -> check(audioTrackIndex >= 0) { "Audio track is not ready" }.let { audioTrackIndex }
            }

        private fun copySample(
            kind: MuxerTrack,
            source: ByteBuffer,
            bufferInfo: MediaCodec.BufferInfo,
        ): PendingSample {
            val duplicate = source.duplicate()
            duplicate.position(bufferInfo.offset)
            duplicate.limit(bufferInfo.offset + bufferInfo.size)
            val bytes = ByteArray(bufferInfo.size)
            duplicate.get(bytes)
            return PendingSample(kind, bytes, bufferInfo.presentationTimeUs, bufferInfo.flags)
        }

        private data class PendingSample(
            val kind: MuxerTrack,
            val bytes: ByteArray,
            val presentationTimeUs: Long,
            val flags: Int,
        )
    }

    private data class TerminationPlan(
        val completion: CompletableFuture<ScreenRecordingEncodingResult>,
        val leader: Boolean,
    )

    companion object {
        private const val VIRTUAL_DISPLAY_NAME = "SuperIslandScreenRecording"
        private const val VIDEO_I_FRAME_INTERVAL_SECONDS = 1
        private const val AUDIO_SAMPLE_RATE = 48_000
        private const val AUDIO_CHANNEL_COUNT = 1
        private const val AUDIO_BIT_RATE = 128_000
        private const val AUDIO_BYTES_PER_FRAME = 2
        private const val AUDIO_READ_BUFFER_BYTES = 16 * 1024
        private const val MAX_PENDING_MUXER_BYTES = 16L * 1024L * 1024L
        private const val CODEC_DEQUEUE_TIMEOUT_US = 10_000L
        private const val AUDIO_EOS_TIMEOUT_MILLIS = 2_000L
        private const val AUDIO_IDLE_BACKOFF_MILLIS = 2L
        private const val CODEC_EOS_TIMEOUT_MILLIS = 5_000L
        private const val WORKER_STOP_TIMEOUT_MILLIS = 7_000L
        private const val WORKER_EXECUTOR_TIMEOUT_MILLIS = 2_000L
        private const val COMPLETION_TIMEOUT_MILLIS = 10_000L
        private const val NANOS_PER_MILLISECOND = 1_000_000L
        private const val MICROS_PER_SECOND = 1_000_000L

        private fun releaseCodec(codec: MediaCodec) {
            runCatching { codec.stop() }
            runCatching { codec.release() }
        }

        private fun combineFailures(
            first: Throwable?,
            second: Throwable?,
        ): Throwable? =
            when {
                first == null -> second
                second == null -> first
                first === second -> first
                else -> first.also { it.addSuppressed(second) }
            }

        private fun releaseSafely(
            existing: Throwable?,
            release: () -> Unit,
        ): Throwable? =
            try {
                release()
                existing
            } catch (failure: Throwable) {
                combineFailures(existing, failure)
            }

        private fun readPcm16(
            buffer: ByteArray,
            offset: Int,
        ): Int =
            (buffer[offset].toInt() and 0xff) or (buffer[offset + 1].toInt() shl 8)

        private fun writePcm16(
            buffer: ByteArray,
            offset: Int,
            sample: Int,
        ) {
            buffer[offset] = (sample and 0xff).toByte()
            buffer[offset + 1] = (sample shr 8).toByte()
        }
    }
}
