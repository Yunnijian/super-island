package io.github.superisland.model

/** Island presentation options shared by both UI skins and the SystemUI hook. */
data class IslandAppearanceConfig(
    val capsule: CapsuleChrome = CapsuleChrome(),
)

/** Capsule-only appearance domain. Future chrome options remain in this same owner. */
data class CapsuleChrome(
    val colorOsFluidCloudStyleEnabled: Boolean = false,
)

/** Narrow RemotePreferences contract for SystemUI-owned island presentation. */
object SystemUiIslandAppearanceContract {
    const val SCHEMA_VERSION = 1
    // The first View-projection runtime failed device validation and must stay fail-closed.
    const val COLOR_OS_FLUID_CLOUD_RUNTIME_AVAILABLE = false
    const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    const val REMOTE_PREFERENCES = "SuperIslandIslandAppearance"
    const val RELOAD_SENDER_PERMISSION = "io.github.superisland.permission.SEND_RESIDENT_ISLAND"
    const val ACTION_RELOAD = "io.github.superisland.action.RELOAD_ISLAND_APPEARANCE"
    const val KEY_SCHEMA_VERSION = "schema_version"
    const val KEY_COLOR_OS_FLUID_CLOUD_STYLE_ENABLED =
        "capsule.color_os_fluid_cloud_style_enabled"
}
