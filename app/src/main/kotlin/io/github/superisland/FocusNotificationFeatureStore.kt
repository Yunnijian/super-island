package io.github.superisland

import android.content.Context
import androidx.core.content.edit

/** Persists the focus-notification feature gate independently from transient test-event progress. */
internal class FocusNotificationFeatureStore(context: Context) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = preferences.getBoolean(KEY_ENABLED, true)

    fun setEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_ENABLED, enabled) }
    }

    fun reset() {
        preferences.edit { clear() }
    }

    private companion object {
        const val FILE_NAME = "super-island-features"
        const val KEY_ENABLED = "focus-notification-enabled"
    }
}
