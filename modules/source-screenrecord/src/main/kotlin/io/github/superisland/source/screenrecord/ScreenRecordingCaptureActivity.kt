package io.github.superisland.source.screenrecord

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import io.github.superisland.model.ScreenRecordingAudioSource

/**
 * The only entry point that asks Android for microphone and MediaProjection consent.
 *
 * It never launches the recorder from the background and never attempts to reuse an old
 * projection result. QS and the settings page both explicitly start this activity (tile uses
 * [ScreenRecordingTileCaptureActivity]).
 *
 * When [ScreenRecordingProjectMedia] AppOps is already allowed (Root grant), HyperOS may auto
 * complete the system consent path; callers still must pass through this Activity so the
 * one-shot projection token is obtained legally.
 *
 * In-module launches stay in the caller's task. Tile launches use a no-affinity one-shot task;
 * both paths share an edge-to-edge transparent broker window and zero system transition so HyperOS
 * does not briefly repaint the status bar black between the caller and projection consent.
 */
open class ScreenRecordingCaptureActivity : Activity() {
    private var projectionRequested = false
    private var keepCallerInFront = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureBrokerWindow()
        suppressTransitions()
        keepCallerInFront =
            intent.getBooleanExtra(EXTRA_KEEP_CALLER_IN_FRONT, false) ||
                savedInstanceState?.getBoolean(EXTRA_KEEP_CALLER_IN_FRONT) == true ||
                this is ScreenRecordingTileCaptureActivity
        if (savedInstanceState?.getBoolean(STATE_PROJECTION_REQUESTED) == true) {
            projectionRequested = true
        } else {
            requestRequiredConsent()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_PROJECTION_REQUESTED, projectionRequested)
        outState.putBoolean(EXTRA_KEEP_CALLER_IN_FRONT, keepCallerInFront)
        super.onSaveInstanceState(outState)
    }

    override fun finish() {
        suppressTransitions()
        super.finish()
    }

    @Deprecated("The framework MediaProjection consent flow still returns through this callback.")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_MEDIA_PROJECTION) return
        if (resultCode != RESULT_OK || data == null) {
            reportAndFinish("未授予屏幕录制权限")
            return
        }
        runCatching {
            ContextCompat.startForegroundService(
                this,
                ScreenRecordingService.startIntent(this, resultCode, data),
            )
        }.onFailure {
            reportAndFinish("无法启动录屏服务")
        }.onSuccess {
            finishQuietly()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_RECORD_AUDIO) return
        if (grantResults.singleOrNull() != PackageManager.PERMISSION_GRANTED) {
            reportAndFinish("未授予录音权限")
            return
        }
        launchProjectionConsent()
    }

    private fun requestRequiredConsent() {
        val audioSource = ScreenRecordingConfigStore(this).load().audioSource
        if (
            audioSource != ScreenRecordingAudioSource.NONE &&
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO)
            return
        }
        launchProjectionConsent()
    }

    private fun launchProjectionConsent() {
        if (projectionRequested) return
        projectionRequested = true
        val projectionManager = getSystemService(MediaProjectionManager::class.java)
        val intent =
            if (Build.VERSION.SDK_INT >= 34) {
                // Prefer full-display capture over the single-app share picker when the OEM allows it.
                projectionManager.createScreenCaptureIntent(
                    MediaProjectionConfig.createConfigForDefaultDisplay(),
                )
            } else {
                projectionManager.createScreenCaptureIntent()
            }
        // Avoid transition animation into the system consent activity when it is shown.
        startActivityForResult(intent, REQUEST_MEDIA_PROJECTION)
        suppressTransitions()
    }

    private fun reportAndFinish(message: String) {
        ScreenRecordingRuntimeStore(this).save(
            ScreenRecordingRuntimeState(
                phase = ScreenRecordingPhase.ERROR,
                message = message,
            ),
        )
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        finishQuietly()
    }

    private fun finishQuietly() {
        // The tile activity has an empty task affinity, so an ordinary finish tears down only its
        // one-shot task and restores the pre-QS caller without a task-removal transition.
        finish()
    }

    private fun configureBrokerWindow() {
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.setDimAmount(0f)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.setStatusBarContrastEnforced(false)
        window.setNavigationBarContrastEnforced(false)
        window.setDecorFitsSystemWindows(false)
    }

    private fun suppressTransitions() {
        // API 36 is the minimum supported level. The modern override covers shell task
        // transitions; the legacy override still covers the consent result callback path.
        overrideActivityTransition(
            Activity.OVERRIDE_TRANSITION_OPEN,
            0,
            0,
            Color.TRANSPARENT,
        )
        overrideActivityTransition(
            Activity.OVERRIDE_TRANSITION_CLOSE,
            0,
            0,
            Color.TRANSPARENT,
        )
        overridePendingTransition(0, 0, Color.TRANSPARENT)
    }

    companion object {
        private const val REQUEST_RECORD_AUDIO = 51
        private const val REQUEST_MEDIA_PROJECTION = 52
        private const val STATE_PROJECTION_REQUESTED = "projection-requested"
        const val EXTRA_KEEP_CALLER_IN_FRONT = "keep-caller-in-front"

        /** In-module path: same task as MainActivity, no animation. */
        fun captureIntent(
            context: Context,
            keepCallerInFront: Boolean = false,
        ): Intent =
            if (keepCallerInFront) {
                tileCaptureIntent(context)
            } else {
                Intent(context, ScreenRecordingCaptureActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
                    )
                }
            }

        /** QS tile path: no-affinity one-shot component that never reuses MainActivity's task. */
        fun tileCaptureIntent(context: Context): Intent =
            Intent(context, ScreenRecordingTileCaptureActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION,
                )
                putExtra(EXTRA_KEEP_CALLER_IN_FRONT, true)
            }
    }
}
