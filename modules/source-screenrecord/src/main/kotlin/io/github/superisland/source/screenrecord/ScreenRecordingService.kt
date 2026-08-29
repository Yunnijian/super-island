package io.github.superisland.source.screenrecord

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Display
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.superisland.model.ScreenRecordingAudioSource
import io.github.superisland.model.ScreenRecordingConfig
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

/**
 * User-started foreground recording service. It accepts only the one-time MediaProjection result
 * supplied by [ScreenRecordingCaptureActivity]; there is no background capture or token reuse.
 */
class ScreenRecordingService : Service() {
    private val workExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "SuperIslandScreenRecording").apply { priority = Thread.NORM_PRIORITY - 1 }
    }
    private val runtimeStore by lazy { ScreenRecordingRuntimeStore(this) }
    private val configStore by lazy { ScreenRecordingConfigStore(this) }
    private val storage by lazy { ScreenRecordingStorage(this) }
    private val rootSettings by lazy { ScreenRecordingRootSettings(this) }
    private val lifecycleLock = Any()
    private val started = AtomicBoolean(false)
    private val stopRequested = AtomicBoolean(false)
    private val recordingPaused = AtomicBoolean(false)
    private val focusRowVisible = AtomicBoolean(false)

    private var cleanupFinished = false
    private var encoder: ScreenRecordingEncoder? = null
    private var output: ScreenRecordingOutput? = null
    private var rootSession: ScreenRecordingRootSession? = null
    private var activeConfig: ScreenRecordingConfig? = null
    private var screenOffReceiverRegistered = false
    private val focusNotification by lazy { ScreenRecordingFocusNotification(this) }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val recordingClock = PausableRecordingClock { SystemClock.elapsedRealtime() }
    private var recordingStartedAtElapsed = 0L
    private val focusRowReveal =
        Runnable {
            if (
                !started.get() ||
                    stopRequested.get() ||
                    recordingStartedAtElapsed == 0L
            ) {
                return@Runnable
            }
            focusRowVisible.set(true)
            val paused = recordingPaused.get()
            notifyActiveFocusNotification(currentElapsedMillis(), paused)
            if (!paused && !stopRequested.get()) {
                mainHandler.postDelayed(islandTicker, ISLAND_TICK_MILLIS)
            }
        }
    private val islandTicker =
        object : Runnable {
            override fun run() {
                if (
                    !started.get() ||
                        stopRequested.get() ||
                        recordingPaused.get() ||
                        recordingStartedAtElapsed == 0L
                ) {
                    return
                }
                notifyActiveFocusNotification(currentElapsedMillis(), paused = false)
                if (!recordingPaused.get() && !stopRequested.get()) {
                    mainHandler.postDelayed(this, ISLAND_TICK_MILLIS)
                }
            }
        }

    private val screenOffReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_SCREEN_OFF) {
                    handleStopRequest("屏幕已熄灭，录屏已停止")
                }
            }
        }

    override fun onCreate() {
        super.onCreate()
        liveInstance = this
        sessionActive.set(true)
        Log.i(TAG, "onCreate sessionActive=true")
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val action = intent?.action
        Log.i(
            TAG,
            "onStartCommand action=$action started=${started.get()} stopRequested=${stopRequested.get()} " +
                "phase=${runtimeStore.load().phase}",
        )
        when (action) {
            ACTION_STOP -> handleStopRequest("录屏已停止")
            ACTION_PAUSE -> handlePauseResumeRequest(targetPaused = true, startId = startId)
            ACTION_RESUME -> handlePauseResumeRequest(targetPaused = false, startId = startId)
            ACTION_START -> startRecording(intent)
            else -> {
                Log.w(TAG, "onStartCommand unknown action=$action → stopSelf")
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        // A normal stop completes cleanup on the worker before stopSelf(). This fallback is for an
        // unexpected service teardown, where restoring the user's two SystemUI settings matters
        // more than retaining an unfinished MP4.
        Log.i(
            TAG,
            "onDestroy cleanupFinished=$cleanupFinished started=${started.get()} " +
                "phase=${runCatching { runtimeStore.load().phase }.getOrNull()}",
        )
        mainHandler.removeCallbacks(focusRowReveal)
        mainHandler.removeCallbacks(islandTicker)
        focusRowVisible.set(false)
        recordingClock.reset()
        val mustCleanUp = synchronized(lifecycleLock) { !cleanupFinished }
        if (mustCleanUp) {
            runCatching { encoder?.close() }
            runCatching { output?.let(storage::discard) }
            runCatching { rootSession?.restore() }
            unregisterScreenOffReceiver()
            // Process/service death left phase at preparing/recording; clear so UI is not stuck on
            // "停止录制" with no live session.
            if (runtimeStore.load().phase.isActive) {
                runtimeStore.save(
                    ScreenRecordingRuntimeState(
                        phase = ScreenRecordingPhase.IDLE,
                        message = "上一次录制已中断",
                    ),
                )
            }
        }
        if (liveInstance === this) {
            liveInstance = null
        }
        sessionActive.set(false)
        workExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun startRecording(startIntent: Intent) {
        if (!started.compareAndSet(false, true)) return
        val projectionResultCode = startIntent.getIntExtra(EXTRA_PROJECTION_RESULT_CODE, Int.MIN_VALUE)
        val projectionData = startIntent.getParcelableExtra(EXTRA_PROJECTION_DATA, Intent::class.java)
        if (projectionResultCode == Int.MIN_VALUE || projectionData == null) {
            finishFailedStart(IllegalArgumentException("缺少系统屏幕录制授权"))
            return
        }

        val config = configStore.load()
        activeConfig = config
        try {
            startForeground(
                ScreenRecordingFocusNotification.NOTIFICATION_ID,
                focusNotification.buildActive(
                    elapsedMillis = 0L,
                    paused = false,
                    audioSource = config.audioSource,
                    pauseResumeIntent = pauseResumePendingIntent(currentlyPaused = false),
                    stopIntent = stopPendingIntent(),
                    showInNotificationShade = false,
                ),
                foregroundServiceType(config),
            )
        } catch (failure: Throwable) {
            finishFailedStart(failure)
            return
        }
        runtimeStore.save(
            ScreenRecordingRuntimeState(
                phase = ScreenRecordingPhase.PREPARING,
                message = "正在准备录屏",
            ),
        )
        workExecutor.execute {
            startOnWorker(
                config = config,
                projectionResultCode = projectionResultCode,
                projectionData = projectionData,
            )
        }
    }

    private fun startOnWorker(
        config: ScreenRecordingConfig,
        projectionResultCode: Int,
        projectionData: Intent,
    ) {
        // The encoder owns the MediaProjection once constructed and stops it in its own failure
        // path. Track the session acquired before that point so a mid-startup failure (output
        // creation, encoder construction, or a cancel check) still releases it instead of leaking
        // the restricted projection session until process death.
        var unownedProjection: MediaProjection? = null
        try {
            check(!stopRequested.get()) { "录屏已在启动前取消" }
            if (config.audioSource != ScreenRecordingAudioSource.NONE) {
                check(
                    checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED,
                ) { "录音权限已被撤销" }
            }
            val session = rootSettings.begin(config).getOrElse { throw it }
            rootSession = session
            check(!stopRequested.get()) { "录屏已在 Root 设置准备后取消" }

            val projectionManager = getSystemService(MediaProjectionManager::class.java)
            val projection = projectionManager.getMediaProjection(projectionResultCode, projectionData)
            checkNotNull(projection) { "系统未返回可用的屏幕录制会话" }
            unownedProjection = projection

            val createdOutput = storage.createOutput(config.storageTreeUri)
            output = createdOutput
            check(!stopRequested.get()) { "录屏已在创建文件后取消" }

            val createdEncoder =
                ScreenRecordingEncoder(
                    mediaProjection = projection,
                    config = config,
                    output = createdOutput,
                    display = currentDisplay(),
                    onProjectionStopped = { handleStopRequest("系统已结束屏幕录制") },
                )
            encoder = createdEncoder
            unownedProjection = null
            createdEncoder.start()
            check(!stopRequested.get()) { "录屏已在启动后取消" }
            if (config.stopOnLockScreen) registerScreenOffReceiver()
            recordingClock.start()
            recordingStartedAtElapsed = SystemClock.elapsedRealtime()
            recordingPaused.set(false)
            focusRowVisible.set(false)
            // Keep the first FGS update island-only while the projection activity exits. The first
            // stable reveal shows the silent ColorOS-style Focus row on the same notification ID.
            mainHandler.removeCallbacks(focusRowReveal)
            mainHandler.postDelayed(focusRowReveal, ISLAND_TICK_MILLIS)
            runtimeStore.save(
                ScreenRecordingRuntimeState(
                    phase = ScreenRecordingPhase.RECORDING,
                    message = "正在录制屏幕",
                    startedAtElapsedRealtime = recordingStartedAtElapsed,
                    elapsedDurationMillis = 0L,
                ),
            )
        } catch (failure: Throwable) {
            unownedProjection?.let { projection -> runCatching { projection.stop() } }
            finishRecording(failure)
        }
    }

    private fun handlePauseResumeRequest(
        targetPaused: Boolean,
        startId: Int? = null,
    ) {
        if (!started.get()) {
            if (runtimeStore.load().phase.isActive) {
                runtimeStore.save(
                    ScreenRecordingRuntimeState(
                        phase = ScreenRecordingPhase.IDLE,
                        message = "上一次录制已中断",
                    ),
                )
            }
            if (startId != null) stopSelf(startId) else stopSelf()
            return
        }
        if (stopRequested.get()) return
        runCatching {
            workExecutor.execute {
                if (stopRequested.get()) return@execute
                val activeEncoder = encoder ?: return@execute
                if (recordingPaused.get() == targetPaused) return@execute
                try {
                    if (targetPaused) {
                        pauseOnWorker(activeEncoder)
                    } else {
                        resumeOnWorker(activeEncoder)
                    }
                } catch (failure: Throwable) {
                    if (!stopRequested.get()) finishRecording(failure)
                }
            }
        }.onFailure { failure ->
            Log.e(TAG, "workExecutor rejected pause/resume", failure)
        }
    }

    private fun pauseOnWorker(activeEncoder: ScreenRecordingEncoder) {
        val elapsed = currentElapsedMillis()
        val stateCommitted =
            synchronized(lifecycleLock) {
                if (stopRequested.get() || cleanupFinished) return
                runtimeStore.save(
                    ScreenRecordingRuntimeState(
                        phase = ScreenRecordingPhase.PAUSED,
                        message = "录屏已暂停",
                        elapsedDurationMillis = elapsed,
                    ),
                )
            }
        if (!stateCommitted) {
            Log.e(TAG, "Unable to persist PAUSED state; recording was not paused")
            return
        }

        var encoderPaused = false
        var clockPaused = false
        try {
            activeEncoder.pause()
            encoderPaused = true
            val frozenElapsed = recordingClock.pause()
            clockPaused = true
            recordingPaused.set(true)
            synchronized(lifecycleLock) {
                if (stopRequested.get() || cleanupFinished) return
                mainHandler.removeCallbacks(islandTicker)
                postActiveFocusNotification(frozenElapsed, paused = true)
            }
        } catch (failure: Throwable) {
            val rollbackFailure =
                runCatching {
                    if (encoderPaused) activeEncoder.resume()
                    if (clockPaused) recordingClock.resume()
                    recordingPaused.set(false)
                }.exceptionOrNull()
            if (stopRequested.get() || cleanupFinished) return
            val stateRestored =
                synchronized(lifecycleLock) {
                    if (stopRequested.get() || cleanupFinished) return
                    runtimeStore.save(
                        ScreenRecordingRuntimeState(
                            phase = ScreenRecordingPhase.RECORDING,
                            message = "正在录制屏幕",
                            startedAtElapsedRealtime = recordingStartedAtElapsed,
                            elapsedDurationMillis = currentElapsedMillis(),
                        ),
                    )
                }
            if (rollbackFailure == null && stateRestored) {
                val activeElapsed = currentElapsedMillis()
                postActiveFocusNotification(activeElapsed, paused = false)
                mainHandler.removeCallbacks(islandTicker)
                if (focusRowVisible.get()) {
                    mainHandler.postDelayed(islandTicker, ISLAND_TICK_MILLIS)
                }
                Log.e(TAG, "Unable to pause recording; previous state restored", failure)
                return
            }
            throw transitionFailure("无法切换到录屏暂停状态", failure, rollbackFailure, stateRestored)
        }
    }

    private fun resumeOnWorker(activeEncoder: ScreenRecordingEncoder) {
        val elapsed = currentElapsedMillis()
        var encoderResumed = false
        var clockResumed = false
        try {
            activeEncoder.resume()
            encoderResumed = true
            recordingClock.resume()
            clockResumed = true
            val resumedAt = (SystemClock.elapsedRealtime() - elapsed).coerceAtLeast(0L)
            val stateCommitted =
                synchronized(lifecycleLock) {
                    if (stopRequested.get() || cleanupFinished) return
                    runtimeStore.save(
                        ScreenRecordingRuntimeState(
                            phase = ScreenRecordingPhase.RECORDING,
                            message = "正在录制屏幕",
                            startedAtElapsedRealtime = resumedAt,
                            elapsedDurationMillis = elapsed,
                        ),
                    )
                }
            check(stateCommitted) { "Unable to persist RECORDING state" }
            recordingStartedAtElapsed = resumedAt
            recordingPaused.set(false)
            synchronized(lifecycleLock) {
                if (stopRequested.get() || cleanupFinished) return
                postActiveFocusNotification(elapsed, paused = false)
                mainHandler.removeCallbacks(islandTicker)
                if (focusRowVisible.get()) {
                    mainHandler.postDelayed(islandTicker, ISLAND_TICK_MILLIS)
                }
            }
        } catch (failure: Throwable) {
            val rollbackFailure =
                runCatching {
                    if (encoderResumed) activeEncoder.pause()
                    if (clockResumed) recordingClock.pause()
                    recordingPaused.set(true)
                }.exceptionOrNull()
            if (stopRequested.get() || cleanupFinished) return
            val stateRestored =
                synchronized(lifecycleLock) {
                    if (stopRequested.get() || cleanupFinished) return
                    runtimeStore.save(
                        ScreenRecordingRuntimeState(
                            phase = ScreenRecordingPhase.PAUSED,
                            message = "录屏已暂停",
                            elapsedDurationMillis = currentElapsedMillis(),
                        ),
                    )
                }
            if (rollbackFailure == null && stateRestored) {
                mainHandler.removeCallbacks(islandTicker)
                postActiveFocusNotification(currentElapsedMillis(), paused = true)
                Log.e(TAG, "Unable to resume recording; previous state restored", failure)
                return
            }
            throw transitionFailure("无法继续录屏", failure, rollbackFailure, stateRestored)
        }
    }

    private fun transitionFailure(
        message: String,
        failure: Throwable,
        rollbackFailure: Throwable?,
        stateRestored: Boolean,
    ): IllegalStateException =
        IllegalStateException(message, failure).apply {
            rollbackFailure?.let(::addSuppressed)
            if (!stateRestored) {
                addSuppressed(IllegalStateException("无法恢复录屏运行状态"))
            }
        }

    /**
     * Instance stop path. Prefer calling this directly from [requestStop] when the service lives in
     * the same process — do not rely solely on [Context.startService], which can be denied for
     * typed mediaProjection services on modern HyperOS/Android even when a session is active.
     */
    private fun handleStopRequest(message: String) {
        Log.i(
            TAG,
            "handleStopRequest message=$message started=${started.get()} " +
                "stopRequested=${stopRequested.get()} cleanupFinished=$cleanupFinished",
        )
        if (!started.get()) {
            // Stale STOP after process death or double-tap: never leave phase=recording on disk.
            // Do not call startForeground here — stop must not be started via startForegroundService.
            runtimeStore.save(
                ScreenRecordingRuntimeState(
                    phase = ScreenRecordingPhase.IDLE,
                    message = message,
                ),
            )
            stopSelf()
            return
        }
        val shouldSchedule =
            synchronized(lifecycleLock) {
                if (!stopRequested.compareAndSet(false, true)) {
                    false
                } else {
                    val saved =
                        runtimeStore.save(
                            ScreenRecordingRuntimeState(
                                phase = ScreenRecordingPhase.FINALIZING,
                                message = "正在完成录屏",
                                startedAtElapsedRealtime = recordingStartedAtElapsed,
                                elapsedDurationMillis = currentElapsedMillis(),
                            ),
                        )
                    if (!saved) Log.e(TAG, "Unable to persist FINALIZING state")
                    true
                }
            }
        if (!shouldSchedule) {
            Log.w(TAG, "handleStopRequest ignored: stop already requested")
            return
        }
        Log.i(TAG, "handleStopRequest → FINALIZING, scheduling finishRecording")
        runCatching {
            workExecutor.execute {
                Log.i(TAG, "finishRecording worker begin")
                finishRecording(null, message)
                Log.i(TAG, "finishRecording worker end phase=${runtimeStore.load().phase}")
            }
        }.onFailure { failure ->
            Log.e(TAG, "workExecutor rejected finishRecording", failure)
            finishRecording(failure, message)
        }
    }

    private fun finishFailedStart(failure: Throwable) {
        started.set(true)
        stopRequested.set(true)
        finishRecording(failure)
    }

    private fun finishRecording(
        initialFailure: Throwable?,
        successMessage: String = "录屏已保存",
    ) {
        synchronized(lifecycleLock) {
            if (cleanupFinished) return
            cleanupFinished = true
        }
        stopRequested.set(true)

        var failure = initialFailure
        var savedOutput: ScreenRecordingOutput? = null
        val activeEncoder = encoder
        if (activeEncoder != null && initialFailure == null) {
            val result = runCatching { activeEncoder.stop() }
            result.onSuccess { encoding ->
                if (encoding.muxerStarted && encoding.videoSampleCount > 0) {
                    savedOutput = output
                } else {
                    failure = IllegalStateException("录屏未产生有效视频数据")
                }
            }.onFailure { encoderFailure ->
                failure = encoderFailure
            }
        }
        runCatching { activeEncoder?.close() }.onFailure { closeFailure ->
            if (failure == null) failure = closeFailure
        }

        val activeOutput = output
        val outputToFinalize = savedOutput
        if (outputToFinalize != null && failure == null) {
            runCatching { storage.finalize(outputToFinalize) }.onFailure { finalizeFailure ->
                failure = finalizeFailure
            }
        }
        if (failure != null && activeOutput != null) {
            runCatching { storage.discard(activeOutput) }
        }

        val restoreFailure = rootSession?.restore()?.exceptionOrNull()
        unregisterScreenOffReceiver()
        encoder = null
        output = null
        rootSession = null

        if (restoreFailure != null) {
            val restoreMessage =
                if (failure == null && savedOutput != null) {
                    "Recording was saved, but Root settings could not be restored"
                } else {
                    "Root settings could not be restored during recording cleanup"
                }
            Log.w(TAG, restoreMessage, restoreFailure)
        }
        if (failure != null) {
            Log.w(TAG, "Screen recording ended without a completed output", failure)
        }
        val completionOutputUri = savedOutput?.uri?.toString()
        val completionState =
            ScreenRecordingCompletionPolicy.finishedState(
                recordingFailure = failure,
                outputUri = completionOutputUri,
                rootRestoreFailure = restoreFailure,
                successMessage = successMessage,
                elapsedDurationMillis = currentElapsedMillis(),
            )
        val completionStatePersisted = runtimeStore.saveBlocking(completionState)
        val shouldPublishCompletion =
            ScreenRecordingCompletionPolicy.shouldPublish(
                recordingFailure = failure,
                outputUri = completionOutputUri,
                statePersisted = completionStatePersisted,
            )
        if (failure == null && savedOutput != null && !shouldPublishCompletion) {
            Log.e(TAG, "Saved recording URI was not persisted; suppressing unusable completion card")
        }
        val completionPosted =
            if (shouldPublishCompletion && savedOutput != null) {
                runCatching {
                    NotificationManagerCompat.from(this)
                        .notify(
                            ScreenRecordingFocusNotification.NOTIFICATION_ID,
                            focusNotification.buildCompleted(
                                outputUri = savedOutput.uri,
                                savedToGallery = !savedOutput.isDocumentUri,
                            ),
                        )
                }.onFailure { completionFailure ->
                    Log.w(TAG, "Unable to publish recording completion Focus card", completionFailure)
                }.isSuccess
            } else {
                false
            }
        mainHandler.removeCallbacks(focusRowReveal)
        mainHandler.removeCallbacks(islandTicker)
        focusRowVisible.set(false)
        recordingPaused.set(false)
        recordingStartedAtElapsed = 0L
        recordingClock.reset()
        activeConfig = null
        sessionActive.set(false)
        stopForeground(
            if (completionPosted) {
                STOP_FOREGROUND_DETACH
            } else {
                STOP_FOREGROUND_REMOVE
            },
        )
        stopSelf()
    }

    private fun currentDisplay(): ScreenRecordingDisplay {
        val displayManager = checkNotNull(getSystemService(DisplayManager::class.java))
        val display = checkNotNull(displayManager.getDisplay(Display.DEFAULT_DISPLAY))
        val mode = display.mode
        val maximumRefreshRate =
            display.supportedModes.maxOfOrNull { candidate -> candidate.refreshRate }
                ?: display.refreshRate
        return ScreenRecordingDisplay(
            width = mode.physicalWidth,
            height = mode.physicalHeight,
            densityDpi = resources.configuration.densityDpi,
            maximumFramesPerSecond = maximumRefreshRate.roundToInt().coerceAtLeast(1),
        )
    }

    private fun foregroundServiceType(config: ScreenRecordingConfig): Int =
        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or
            if (config.audioSource == ScreenRecordingAudioSource.NONE) {
                0
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }

    private fun stopPendingIntent(): PendingIntent =
        PendingIntent.getService(
            this,
            STOP_PENDING_INTENT_REQUEST_CODE,
            stopIntent(this),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun pauseResumePendingIntent(currentlyPaused: Boolean): PendingIntent =
        PendingIntent.getService(
            this,
            if (currentlyPaused) {
                RESUME_PENDING_INTENT_REQUEST_CODE
            } else {
                PAUSE_PENDING_INTENT_REQUEST_CODE
            },
            pauseResumeIntent(this, targetPaused = !currentlyPaused),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun activeFocusNotification(
        elapsedMillis: Long,
        paused: Boolean,
    ): Notification =
        focusNotification.buildActive(
            elapsedMillis = elapsedMillis,
            paused = paused,
            audioSource = activeConfig?.audioSource ?: ScreenRecordingAudioSource.NONE,
            pauseResumeIntent = pauseResumePendingIntent(currentlyPaused = paused),
            stopIntent = stopPendingIntent(),
            showInNotificationShade = focusRowVisible.get(),
        )

    private fun notifyActiveFocusNotification(
        elapsedMillis: Long,
        paused: Boolean,
    ) {
        runCatching {
            NotificationManagerCompat.from(this)
                .notify(
                    ScreenRecordingFocusNotification.NOTIFICATION_ID,
                    activeFocusNotification(elapsedMillis, paused),
                )
        }.onFailure { failure ->
            Log.w(TAG, "Unable to update recording Focus card", failure)
        }
    }

    private fun postActiveFocusNotification(
        elapsedMillis: Long,
        paused: Boolean,
    ) {
        mainHandler.post {
            if (
                stopRequested.get() ||
                    !started.get() ||
                    recordingPaused.get() != paused
            ) {
                return@post
            }
            notifyActiveFocusNotification(elapsedMillis, paused)
        }
    }

    private fun currentElapsedMillis(): Long =
        if (recordingStartedAtElapsed == 0L) {
            0L
        } else {
            runCatching(recordingClock::elapsed).getOrDefault(0L)
        }

    private fun registerScreenOffReceiver() {
        if (screenOffReceiverRegistered) return
        ContextCompat.registerReceiver(
            this,
            screenOffReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        screenOffReceiverRegistered = true
    }

    private fun unregisterScreenOffReceiver() {
        if (!screenOffReceiverRegistered) return
        runCatching { unregisterReceiver(screenOffReceiver) }
        screenOffReceiverRegistered = false
    }

    companion object {
        private const val TAG = "SuperIslandScreenRecord"
        private const val ACTION_START = "io.github.superisland.action.START_SCREEN_RECORDING"
        private const val ACTION_STOP = "io.github.superisland.action.STOP_SCREEN_RECORDING"
        private const val ACTION_PAUSE = "io.github.superisland.action.PAUSE_SCREEN_RECORDING"
        private const val ACTION_RESUME = "io.github.superisland.action.RESUME_SCREEN_RECORDING"
        private const val EXTRA_PROJECTION_RESULT_CODE = "projection-result-code"
        private const val EXTRA_PROJECTION_DATA = "projection-data"
        private const val STOP_PENDING_INTENT_REQUEST_CODE = 28_372
        private const val PAUSE_PENDING_INTENT_REQUEST_CODE = 28_375
        private const val RESUME_PENDING_INTENT_REQUEST_CODE = 28_376
        private const val ISLAND_TICK_MILLIS = 1_000L

        /**
         * True only while this process hosts a live [ScreenRecordingService] instance.
         * Process death clears it; UI/tile must reconcile disk phase against this flag.
         */
        private val sessionActive = AtomicBoolean(false)

        /** Same-process service pointer for reliable stop without typed-service start restrictions. */
        @Volatile
        private var liveInstance: ScreenRecordingService? = null

        fun hasActiveSession(): Boolean = sessionActive.get() || liveInstance != null

        fun startIntent(
            context: Context,
            projectionResultCode: Int,
            projectionData: Intent,
        ): Intent =
            Intent(context, ScreenRecordingService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_PROJECTION_RESULT_CODE, projectionResultCode)
                .putExtra(EXTRA_PROJECTION_DATA, projectionData)

        fun stopIntent(context: Context): Intent =
            Intent(context, ScreenRecordingService::class.java).setAction(ACTION_STOP)

        fun pauseResumeIntent(
            context: Context,
            targetPaused: Boolean,
        ): Intent =
            Intent(context, ScreenRecordingService::class.java)
                .setAction(if (targetPaused) ACTION_PAUSE else ACTION_RESUME)

        /**
         * Safe stop for UI/tile.
         *
         * Order:
         * 1. Call the live service instance in-process (reliable).
         * 2. Else clear a stale active phase on disk (no live session).
         * Never uses [ContextCompat.startForegroundService] for stop: a cold mediaProjection FGS
         * start without a token crashes and can kick the user to the launcher.
         */
        fun requestStop(context: Context, message: String = "录屏已停止") {
            val app = context.applicationContext
            val store = ScreenRecordingRuntimeStore(app)
            val before = store.load()
            val instance = liveInstance
            Log.i(
                TAG,
                "requestStop(ui) phase=${before.phase} message=${before.message} " +
                    "sessionActive=${sessionActive.get()} liveInstance=${instance != null}",
            )
            if (instance != null) {
                instance.handleStopRequest(message)
                val after = store.load()
                Log.i(TAG, "requestStop(ui) after direct handle phase=${after.phase}")
                return
            }
            if (before.phase.isActive) {
                store.save(
                    ScreenRecordingRuntimeState(
                        phase = ScreenRecordingPhase.IDLE,
                        message = message,
                    ),
                )
                Log.w(TAG, "requestStop(ui) no live instance → forced IDLE (was ${before.phase})")
            } else {
                Log.i(TAG, "requestStop(ui) no-op: already ${before.phase}")
            }
            sessionActive.set(false)
        }

        /** Delivers pause/resume only to the live in-process service; never cold-starts projection. */
        fun requestPauseResume(
            context: Context,
            targetPaused: Boolean,
        ) {
            val app = context.applicationContext
            val instance = liveInstance
            if (instance != null) {
                instance.handlePauseResumeRequest(targetPaused = targetPaused)
            } else {
                reconcileRuntime(app)
            }
        }

        /**
         * If SharedPreferences still says preparing/recording/finalizing but this process has no
         * live service, clear to IDLE so the detail page shows「开始录制」again.
         */
        fun reconcileRuntime(context: Context): ScreenRecordingRuntimeState {
            val store = ScreenRecordingRuntimeStore(context.applicationContext)
            val current = store.load()
            val active = hasActiveSession()
            if (current.phase.isActive && !active) {
                val cleared =
                    ScreenRecordingRuntimeState(
                        phase = ScreenRecordingPhase.IDLE,
                        message = "上一次录制已中断",
                    )
                store.save(cleared)
                Log.w(
                    TAG,
                    "reconcileRuntime cleared stale phase=${current.phase} " +
                        "sessionActive=${sessionActive.get()} liveInstance=${liveInstance != null}",
                )
                return cleared
            }
            return current
        }
    }
}
