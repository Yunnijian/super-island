package io.github.superisland.source.screenrecord

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.readText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenRecordingRootSettingsTest {
    @Test
    fun `only one authenticated SystemUI response completes a pending request`() {
        val requestId = "8ba1fbd6-6bd2-4f4f-8822-7d7b2052e6ea"
        val future = ScreenRecordingRootControlResults.register(requestId)

        assertTrue(
            ScreenRecordingRootControlResults.reportFromSystemUi(
                requestId = requestId,
                success = true,
                reason = "",
                originalShowTouches = false,
                originalProtection = true,
                originalProtectionOn = true,
            ),
        )
        assertTrue(future.get().success)
        assertFalse(
            ScreenRecordingRootControlResults.reportFromSystemUi(
                requestId = requestId,
                success = true,
                reason = "duplicate",
                originalShowTouches = false,
                originalProtection = true,
                originalProtectionOn = true,
            ),
        )
    }

    @Test
    fun `malformed request ids cannot be registered or reported`() {
        assertFalse(
            ScreenRecordingRootControlResults.reportFromSystemUi(
                requestId = "bad id",
                success = false,
                reason = "bad",
                originalShowTouches = false,
                originalProtection = false,
                originalProtectionOn = false,
            ),
        )
    }

    @Test
    fun `root prepare is best effort and must not hard-fail the MediaProjection path`() {
        val rootSettings = sourceFile(
            "modules/source-screenrecord/src/main/kotlin/io/github/superisland/source/screenrecord/ScreenRecordingRootSettings.kt",
        ).readText()
        val bridge = sourceFile(
            "modules/hook-systemui/src/main/java/io/github/superisland/hook/systemui/SystemUiScreenRecordingRootBridge.java",
        ).readText()

        assertTrue(rootSettings.contains("recording continues without Root settings"))
        assertTrue(rootSettings.contains("SystemUI Root 设置桥接超时"))
        assertTrue(rootSettings.contains("TimeoutException"))
        assertTrue(rootSettings.contains("recover"))
        assertTrue(bridge.contains("isAuthorizedModuleSender"))
        assertTrue(bridge.contains("getSentFromPackage"))
        assertTrue(bridge.contains("SENDER_PERMISSION receiver gate"))
        assertFalse(bridge.contains("isTrustedModuleSender(context, getSentFromUid())"))
    }

}
