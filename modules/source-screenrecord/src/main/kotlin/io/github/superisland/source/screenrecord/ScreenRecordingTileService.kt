package io.github.superisland.source.screenrecord

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.superisland.model.ScreenRecordingTileStyle

/**
 * Quick Settings control. Starts [ScreenRecordingCaptureActivity] in an isolated no-history task
 * so the module settings UI is not brought to the front when the user is on another app.
 */
class ScreenRecordingTileService : TileService() {
    private val runtimeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refreshTile() }

    override fun onStartListening() {
        super.onStartListening()
        ScreenRecordingRuntimeStore(this).observe(runtimeListener)
        refreshTile()
    }

    override fun onStopListening() {
        ScreenRecordingRuntimeStore(this).stopObserving(runtimeListener)
        super.onStopListening()
    }

    override fun onClick() {
        val runtime = ScreenRecordingService.reconcileRuntime(this)
        if (runtime.phase.isActive) {
            ScreenRecordingService.requestStop(this)
        } else {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this,
                    CAPTURE_ACTIVITY_REQUEST_CODE,
                    ScreenRecordingCaptureActivity.tileCaptureIntent(this),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
        }
        refreshTile()
    }

    private fun refreshTile() {
        val runtime = ScreenRecordingService.reconcileRuntime(this)
        val config = ScreenRecordingConfigStore(this).load()
        qsTile?.apply {
            state = if (runtime.phase.isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label =
                if (runtime.phase.isActive) {
                    "正在录制"
                } else {
                    getString(R.string.screen_recording_tile_label)
                }
            icon =
                when (config.tileStyle) {
                    ScreenRecordingTileStyle.RECORDING ->
                        Icon.createWithResource(this@ScreenRecordingTileService, R.drawable.ic_screen_recording)
                    ScreenRecordingTileStyle.APP_ICON ->
                        Icon.createWithResource(
                            this@ScreenRecordingTileService,
                            applicationInfo.icon.takeIf { it != 0 } ?: R.drawable.ic_screen_recording,
                        )
                }
            updateTile()
        }
    }

    companion object {
        const val CAPTURE_ACTIVITY_REQUEST_CODE = 8_381

        /**
         * Active tiles only bind when clicked or explicitly asked to refresh. Calling this while
         * the module process is alive preserves live recording updates without letting SystemUI
         * bind the tile just to refresh a force-stopped module.
         */
        fun requestRefresh(context: Context) {
            val appContext = context.applicationContext
            try {
                TileService.requestListeningState(
                    appContext,
                    ComponentName(appContext, ScreenRecordingTileService::class.java),
                )
            } catch (_: RuntimeException) {
                // A rejected best-effort visual refresh must not invalidate persisted state.
            }
        }
    }
}
