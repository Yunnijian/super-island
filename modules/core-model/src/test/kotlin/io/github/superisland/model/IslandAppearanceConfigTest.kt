package io.github.superisland.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandAppearanceConfigTest {
    @Test
    fun `ColorOS fluid cloud style defaults off`() {
        assertFalse(IslandAppearanceConfig().capsule.colorOsFluidCloudStyleEnabled)
        assertFalse(SystemUiIslandAppearanceContract.COLOR_OS_FLUID_CLOUD_RUNTIME_AVAILABLE)
    }

    @Test
    fun `capsule option remains in island appearance owner`() {
        val config =
            IslandAppearanceConfig(
                capsule = CapsuleChrome(colorOsFluidCloudStyleEnabled = true),
            )

        assertTrue(config.capsule.colorOsFluidCloudStyleEnabled)
    }

    @Test
    fun `SystemUI remote preferences schema keeps stable field names`() {
        assertEquals(1, SystemUiIslandAppearanceContract.SCHEMA_VERSION)
        assertEquals(
            "SuperIslandIslandAppearance",
            SystemUiIslandAppearanceContract.REMOTE_PREFERENCES,
        )
        assertEquals(
            "schema_version",
            SystemUiIslandAppearanceContract.KEY_SCHEMA_VERSION,
        )
        assertEquals(
            "capsule.color_os_fluid_cloud_style_enabled",
            SystemUiIslandAppearanceContract.KEY_COLOR_OS_FLUID_CLOUD_STYLE_ENABLED,
        )
        assertEquals(
            "io.github.superisland.action.RELOAD_ISLAND_APPEARANCE",
            SystemUiIslandAppearanceContract.ACTION_RELOAD,
        )
        assertEquals(
            "com.android.systemui",
            SystemUiIslandAppearanceContract.SYSTEM_UI_PACKAGE,
        )
    }
}
