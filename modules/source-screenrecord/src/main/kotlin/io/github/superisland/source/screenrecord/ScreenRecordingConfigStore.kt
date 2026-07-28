package io.github.superisland.source.screenrecord

import android.content.Context
import android.content.SharedPreferences
import io.github.superisland.model.SCREEN_RECORDING_CONFIG_SCHEMA_VERSION
import io.github.superisland.model.ScreenRecordingAudioSource
import io.github.superisland.model.ScreenRecordingBitrate
import io.github.superisland.model.ScreenRecordingConfig
import io.github.superisland.model.ScreenRecordingFrameRate
import io.github.superisland.model.ScreenRecordingOrientation
import io.github.superisland.model.ScreenRecordingResolution
import io.github.superisland.model.ScreenRecordingTileStyle
import io.github.superisland.model.ScreenRecordingVideoCodec

/**
 * Single-process persisted ownership for the recording extension. All persisted strings are
 * closed enum wire values; malformed or obsolete values reset only this extension to defaults.
 */
class ScreenRecordingConfigStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences =
        appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): ScreenRecordingConfig {
        val schema = preferences.getInt(KEY_SCHEMA, 0)
        if (schema != 1 && schema != SCREEN_RECORDING_CONFIG_SCHEMA_VERSION) {
            return ScreenRecordingConfig()
        }

        val base =
            ScreenRecordingConfig(
                schemaVersion = schema,
                resolution =
                    ScreenRecordingResolution.fromWire(preferences.getString(KEY_RESOLUTION, null))
                        ?: return ScreenRecordingConfig(),
                bitrate =
                    ScreenRecordingBitrate.fromWire(preferences.getString(KEY_BITRATE, null))
                        ?: return ScreenRecordingConfig(),
                orientation =
                    ScreenRecordingOrientation.fromWire(preferences.getString(KEY_ORIENTATION, null))
                        ?: return ScreenRecordingConfig(),
                audioSource =
                    ScreenRecordingAudioSource.fromWire(preferences.getString(KEY_AUDIO_SOURCE, null))
                        ?: return ScreenRecordingConfig(),
                frameRate =
                    ScreenRecordingFrameRate.fromWire(preferences.getString(KEY_FRAME_RATE, null))
                        ?: return ScreenRecordingConfig(),
                videoCodec =
                    ScreenRecordingVideoCodec.fromWire(preferences.getString(KEY_VIDEO_CODEC, null))
                        ?: return ScreenRecordingConfig(),
                showTouches = preferences.getBoolean(KEY_SHOW_TOUCHES, false),
                stopOnLockScreen = preferences.getBoolean(KEY_STOP_ON_LOCK, false),
                bypassScreenShareProtection = preferences.getBoolean(KEY_BYPASS_PROTECTION, false),
                confirmBeforeStart =
                    if (schema >= 2) {
                        preferences.getBoolean(KEY_CONFIRM_BEFORE_START, true)
                    } else {
                        true
                    },
                projectMediaEnabled =
                    if (schema >= 2) {
                        preferences.getBoolean(KEY_PROJECT_MEDIA_ENABLED, false)
                    } else {
                        false
                    },
                tileStyle =
                    ScreenRecordingTileStyle.fromWire(preferences.getString(KEY_TILE_STYLE, null))
                        ?: return ScreenRecordingConfig(),
                storageTreeUri = preferences.getString(KEY_STORAGE_TREE_URI, "").orEmpty(),
            ).normalized()

        // Persist migrated schema so later loads stay on v2 without rewriting user choices.
        if (schema != SCREEN_RECORDING_CONFIG_SCHEMA_VERSION) {
            save(base)
        }
        return base
    }

    /** Uses commit so a failed persistence is observable by the UI owner and can be rolled back. */
    fun save(config: ScreenRecordingConfig): Boolean {
        val normalized = config.normalized()
        val saved =
            preferences
            .edit()
            .putInt(KEY_SCHEMA, normalized.schemaVersion)
            .putString(KEY_RESOLUTION, normalized.resolution.wireValue)
            .putString(KEY_BITRATE, normalized.bitrate.wireValue)
            .putString(KEY_ORIENTATION, normalized.orientation.wireValue)
            .putString(KEY_AUDIO_SOURCE, normalized.audioSource.wireValue)
            .putString(KEY_FRAME_RATE, normalized.frameRate.wireValue)
            .putString(KEY_VIDEO_CODEC, normalized.videoCodec.wireValue)
            .putBoolean(KEY_SHOW_TOUCHES, normalized.showTouches)
            .putBoolean(KEY_STOP_ON_LOCK, normalized.stopOnLockScreen)
            .putBoolean(KEY_BYPASS_PROTECTION, normalized.bypassScreenShareProtection)
            .putBoolean(KEY_CONFIRM_BEFORE_START, normalized.confirmBeforeStart)
            .putBoolean(KEY_PROJECT_MEDIA_ENABLED, normalized.projectMediaEnabled)
            .putString(KEY_TILE_STYLE, normalized.tileStyle.wireValue)
            .putString(KEY_STORAGE_TREE_URI, normalized.storageTreeUri)
            .commit()
        if (saved) {
            ScreenRecordingTileService.requestRefresh(appContext)
        }
        return saved
    }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
    }

    companion object {
        private const val PREFERENCES_NAME = "screen-recording-config"
        private const val KEY_SCHEMA = "schema"
        private const val KEY_RESOLUTION = "resolution"
        private const val KEY_BITRATE = "bitrate"
        private const val KEY_ORIENTATION = "orientation"
        private const val KEY_AUDIO_SOURCE = "audio-source"
        private const val KEY_FRAME_RATE = "frame-rate"
        private const val KEY_VIDEO_CODEC = "video-codec"
        private const val KEY_SHOW_TOUCHES = "show-touches"
        private const val KEY_STOP_ON_LOCK = "stop-on-lock"
        private const val KEY_BYPASS_PROTECTION = "bypass-screen-share-protection"
        private const val KEY_CONFIRM_BEFORE_START = "confirm-before-start"
        private const val KEY_PROJECT_MEDIA_ENABLED = "project-media-enabled"
        private const val KEY_TILE_STYLE = "tile-style"
        private const val KEY_STORAGE_TREE_URI = "storage-tree-uri"
    }
}
