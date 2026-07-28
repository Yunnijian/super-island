package io.github.superisland

import android.content.Context
import android.content.SharedPreferences
import io.github.superisland.model.CapsuleChrome
import io.github.superisland.model.IslandAppearanceConfig
import io.github.superisland.model.SystemUiIslandAppearanceContract

/** App-private persistence owner for island appearance options. */
class IslandAppearanceStore(context: Context) {
    private val preferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun load(): IslandAppearanceConfig =
        IslandAppearanceConfig(
            capsule =
                CapsuleChrome(
                    colorOsFluidCloudStyleEnabled =
                        SystemUiIslandAppearanceContract.COLOR_OS_FLUID_CLOUD_RUNTIME_AVAILABLE &&
                            preferences.getBoolean(
                                KEY_COLOR_OS_FLUID_CLOUD_STYLE_ENABLED,
                                false,
                            ),
                ),
        )

    fun save(config: IslandAppearanceConfig): Result<Unit> =
        runCatching {
            check(
                preferences
                    .edit()
                    .putInt(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION)
                    .putBoolean(
                        KEY_COLOR_OS_FLUID_CLOUD_STYLE_ENABLED,
                        SystemUiIslandAppearanceContract.COLOR_OS_FLUID_CLOUD_RUNTIME_AVAILABLE &&
                            config.capsule.colorOsFluidCloudStyleEnabled,
                    ).commit(),
            ) { "Could not persist island appearance settings" }
        }

    fun reset(): Result<Unit> = save(IslandAppearanceConfig())

    fun observe(onChanged: (IslandAppearanceConfig) -> Unit): () -> Unit {
        val listener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key in OBSERVED_KEYS) onChanged(load())
            }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onChanged(load())
        return { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    private companion object {
        const val FILE_NAME = "island-appearance"
        const val CURRENT_SCHEMA_VERSION = 1
        const val KEY_SCHEMA_VERSION = "schema-version"
        const val KEY_COLOR_OS_FLUID_CLOUD_STYLE_ENABLED =
            "capsule.color-os-fluid-cloud-style-enabled"
        val OBSERVED_KEYS =
            setOf(
                KEY_SCHEMA_VERSION,
                KEY_COLOR_OS_FLUID_CLOUD_STYLE_ENABLED,
            )
    }
}
