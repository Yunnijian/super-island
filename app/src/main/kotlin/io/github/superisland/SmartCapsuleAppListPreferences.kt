package io.github.superisland

import android.content.Context
import io.github.superisland.design.SmartCapsuleAppSortConfig
import io.github.superisland.design.SmartCapsuleAppSortType

internal class SmartCapsuleAppListPreferences(context: Context) {
    private val preferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun showSystemApps(): Boolean = preferences.getBoolean(KEY_SHOW_SYSTEM_APPS, false)

    fun sortConfig(): SmartCapsuleAppSortConfig {
        val type =
            preferences
                .getString(KEY_SORT_TYPE, null)
                ?.let { saved ->
                    SmartCapsuleAppSortType.entries.firstOrNull { type -> type.name == saved }
                } ?: SmartCapsuleAppSortType.NAME
        return SmartCapsuleAppSortConfig(
            type = type,
            reversed = preferences.getBoolean(KEY_SORT_REVERSED, false),
        )
    }

    fun setShowSystemApps(show: Boolean) {
        preferences.edit().putBoolean(KEY_SHOW_SYSTEM_APPS, show).apply()
    }

    fun setSortConfig(config: SmartCapsuleAppSortConfig) {
        preferences
            .edit()
            .putString(KEY_SORT_TYPE, config.type.name)
            .putBoolean(KEY_SORT_REVERSED, config.reversed)
            .apply()
    }

    private companion object {
        const val FILE_NAME = "smart-capsule-app-list"
        const val KEY_SHOW_SYSTEM_APPS = "show_system_apps"
        const val KEY_SORT_TYPE = "sort_type"
        const val KEY_SORT_REVERSED = "sort_reversed"
    }
}
