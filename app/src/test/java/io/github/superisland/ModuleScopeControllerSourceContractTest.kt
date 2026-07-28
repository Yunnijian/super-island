package io.github.superisland

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.readText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Home “重启作用域” must reload every package in META-INF/xposed/scope.list,
 * not only SystemUI.
 */
class ModuleScopeControllerSourceContractTest {
    @Test
    fun homeRestartAlignsWithAllLposedScopes() {
        val scope =
            sourceFile("app/src/main/resources/META-INF/xposed/scope.list")
                .readText()
                .lineSequence()
                .map(String::trim)
                .filter(String::isNotEmpty)
                .toSet()
        val controller = sourceFile("app/src/main/kotlin/io/github/superisland/ModuleScopeController.kt").readText()
        val homeScreen = sourceFile("app/src/main/kotlin/io/github/superisland/ui/home/HomeScreen.kt").readText()
        val homeMiuix = sourceFile("app/src/main/kotlin/io/github/superisland/ui/home/HomeMiuix.kt").readText()
        val homeMaterial = sourceFile("app/src/main/kotlin/io/github/superisland/ui/material/MaterialHome.kt").readText()

        assertEquals(
            setOf(
                "com.android.systemui",
                "com.xiaomi.xmsf",
                "com.miui.mishare.connectivity",
            ),
            scope,
        )
        scope.forEach { packageName ->
            assertTrue(
                "ModuleScopeController must restart scope package $packageName",
                packageName in controller,
            )
        }
        assertTrue("SystemUI must use pkill", """pkill -f com.android.systemui""" in controller)
        assertTrue("XMSF must use pkill", """pkill -f com.xiaomi.xmsf""" in controller)
        assertTrue(
            "MiShare must use force-stop per install docs",
            "am force-stop com.miui.mishare.connectivity" in controller,
        )
        assertTrue(
            "Restart must wait for SystemUI recovery before killing XMSF",
            "waitForPackageProcess" in controller && "SYSTEM_UI_SETTLE_MILLIS" in controller,
        )
        val systemUiKillAt = controller.indexOf("""pkill -f com.android.systemui""")
        val xmsfKillAt = controller.indexOf("""pkill -f com.xiaomi.xmsf""")
        assertTrue("SystemUI must be killed before XMSF", systemUiKillAt in 0 until xmsfKillAt)
        assertTrue("Home must call ModuleScopeController", "ModuleScopeController.restart()" in homeScreen)
        assertTrue(
            "Home must cancel resident island before scope restart",
            "SystemUiResidentIslandPublisher" in homeScreen && ".cancel()" in homeScreen,
        )
        assertTrue("Home action must restart all scopes", "restartScopes" in homeScreen)
        assertFalse("Home must not keep SystemUI-only restart controller", "SystemUiScopeController" in homeScreen)
        listOf(homeMiuix, homeMaterial).forEach { ui ->
            assertTrue(
                "UI summary must stay concise",
                "重启相关作用域，使模块配置立即生效" in ui,
            )
            assertFalse("UI must not claim SystemUI-only restart", "仅重启 SystemUI" in ui)
            assertFalse("UI must not list every scope package in the summary", "SystemUI、XMSF" in ui)
            assertTrue("Maintenance id must be restart_scopes", """"restart_scopes"""" in ui)
        }
    }

}
