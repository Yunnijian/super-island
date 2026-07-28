package io.github.superisland.source.screenrecord

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri

/**
 * Small persisted runtime state shared by the foreground service, the QS tile, and the settings
 * screen. It contains no projection token or configuration, so a process restart cannot reuse a
 * user-consent grant.
 */
class ScreenRecordingRuntimeStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences =
        appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): ScreenRecordingRuntimeState =
        ScreenRecordingRuntimeState(
            phase =
                ScreenRecordingPhase.fromWire(preferences.getString(KEY_PHASE, null))
                    ?: ScreenRecordingPhase.IDLE,
            message = preferences.getString(KEY_MESSAGE, "").orEmpty().take(MAX_MESSAGE_LENGTH),
            outputUri = preferences.getString(KEY_OUTPUT_URI, "").orEmpty().takeIf(::isSafeOutputUri),
            startedAtElapsedRealtime =
                preferences.getLong(KEY_STARTED_AT_ELAPSED, 0L).coerceAtLeast(0L),
        )

    fun save(state: ScreenRecordingRuntimeState): Boolean {
        val ok =
            preferences
                .edit()
                .putString(KEY_PHASE, state.phase.wireValue)
                .putString(KEY_MESSAGE, state.message.take(MAX_MESSAGE_LENGTH))
                .putString(KEY_OUTPUT_URI, state.outputUri.orEmpty())
                .putLong(KEY_STARTED_AT_ELAPSED, state.startedAtElapsedRealtime.coerceAtLeast(0L))
                .commit()
        if (ok) {
            // Always mirror to the in-process hub so Compose does not depend only on prefs listeners.
            ScreenRecordingRuntimeHub.publish(state)
            ScreenRecordingTileService.requestRefresh(appContext)
        }
        return ok
    }

    fun observe(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences.registerOnSharedPreferenceChangeListener(listener)
    }

    fun stopObserving(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
    }

    companion object {
        private const val PREFERENCES_NAME = "screen-recording-runtime"
        private const val KEY_PHASE = "phase"
        private const val KEY_MESSAGE = "message"
        private const val KEY_OUTPUT_URI = "output-uri"
        private const val KEY_STARTED_AT_ELAPSED = "started-at-elapsed"
        private const val MAX_MESSAGE_LENGTH = 160

        private fun isSafeOutputUri(value: String): Boolean =
            value.isEmpty() || runCatching { Uri.parse(value).scheme == "content" }.getOrDefault(false)
    }
}

enum class ScreenRecordingPhase(val wireValue: String) {
    IDLE("idle"),
    PREPARING("preparing"),
    RECORDING("recording"),
    FINALIZING("finalizing"),
    ERROR("error"),
    ;

    val isActive: Boolean
        get() = this == PREPARING || this == RECORDING || this == FINALIZING

    companion object {
        fun fromWire(value: String?): ScreenRecordingPhase? =
            entries.firstOrNull { it.wireValue == value }
    }
}

data class ScreenRecordingRuntimeState(
    val phase: ScreenRecordingPhase = ScreenRecordingPhase.IDLE,
    val message: String = "",
    val outputUri: String? = null,
    /** [android.os.SystemClock.elapsedRealtime] when recording entered RECORDING; 0 when idle. */
    val startedAtElapsedRealtime: Long = 0L,
)
