package io.github.superisland

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.readText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the cross-process update path that previously flooded and stalled SystemUI. */
class ResidentRefreshIntervalSourceContractTest {
    @Test
    fun bothSkinsCommitTheRefreshIntervalOnlyAfterTheDragFinishes() {
        val miuix =
            declaration(
                sourceFile(
                    "modules/ui-design-system/src/main/kotlin/io/github/superisland/design/Screens.kt",
                ).readText(),
                "ResidentMonitorConfigurationScreen",
            )
        val material =
            declaration(
                sourceFile(
                    "app/src/main/kotlin/io/github/superisland/ui/material/MaterialFeatureScreens.kt",
                ).readText(),
                "MaterialResidentMonitorConfigurationScreen",
            )

        listOf(
            "Miuix" to (miuix to invocationArguments(miuix, "SliderPreference")),
            "Material" to (material to invocationArguments(material, "Slider")),
        ).forEach { (skin, sources) ->
            val (screen, slider) = sources
            assertTrue(
                "$skin must reset its local draft when the persisted interval changes",
                Regex("""remember\s*\(\s*refreshIntervalSeconds\s*\)""").containsMatchIn(screen),
            )
            assertTrue(
                "$skin must update only the local draft during pointer movement",
                Regex(
                    """onValueChange\s*=\s*\{[\s\S]*?refreshIntervalDraft\s*=""",
                ).containsMatchIn(slider.substringBefore("onValueChangeFinished")),
            )
            assertFalse(
                "$skin must not persist from raw onValueChange callbacks",
                "onRefreshIntervalChange" in slider.substringBefore("onValueChangeFinished"),
            )
            assertTrue(
                "$skin must persist the final draft from onValueChangeFinished",
                Regex(
                    """onValueChangeFinished\s*=\s*\{[\s\S]*?onRefreshIntervalChange\s*\(\s*refreshIntervalDraft\s*\)""",
                ).containsMatchIn(slider),
            )
            assertTrue(
                "$skin must skip a no-op final value",
                "refreshIntervalDraft != refreshIntervalSeconds" in slider,
            )
        }
    }

    @Test
    fun configSaveAndSystemUiReloadHaveSingleCoalescedOwners() {
        val mainActivity =
            sourceFile("app/src/main/kotlin/io/github/superisland/MainActivity.kt").readText()
        val service =
            sourceFile("app/src/main/kotlin/io/github/superisland/BatteryMonitorService.kt").readText()
        val store =
            sourceFile("app/src/main/kotlin/io/github/superisland/ResidentMonitorConfigStore.kt").readText()
        val host =
            sourceFile(
                "modules/hook-systemui/src/main/java/io/github/superisland/hook/systemui/SystemUiResidentIslandHost.java",
            ).readText()

        val saveResidentConfig = declaration(mainActivity, "saveResidentConfig")
        assertTrue(
            "The app must reject an equal normalized config before touching preferences",
            Regex("""if\s*\(\s*normalized\s*==\s*residentConfig\s*\)\s*return""")
                .containsMatchIn(saveResidentConfig),
        )

        val storeSave = declaration(store, "save")
        assertTrue(
            "The config repository must expose its one SystemUI mirror result to callers",
            "return ResidentIslandHostConfigSync.sync(normalized)" in storeSave,
        )
        assertTrue(
            "A config save must own exactly one cross-process mirror",
            Regex("""ResidentIslandHostConfigSync\.sync""").findAll(storeSave).count() == 1,
        )

        listOf(
            "onResidentMonitorConfigurationChanged" to
                declaration(service, "onResidentMonitorConfigurationChanged"),
            "applySavedConfiguration" to declaration(service, "applySavedConfiguration"),
            "start" to declaration(service, "start"),
            "stop" to declaration(service, "stop"),
            "stopMonitoring" to declaration(service, "stopMonitoring"),
        ).forEach { (name, function) ->
            assertFalse(
                "$name must not duplicate the store-owned SystemUI preference mirror",
                "ResidentIslandHostConfigSync.sync" in function,
            )
        }

        assertTrue(
            "Every RemotePreferences key callback must enter the coalesced apply path",
            Regex(
                """PREFERENCES_LISTENER\s*=[\s\S]*?->\s*requestPersistedSettingsApply\s*\(\s*\)""",
            ).containsMatchIn(host),
        )
        assertTrue(
            "The explicit reload broadcast must enter the same coalesced apply path",
            Regex(
                """ACTION_RELOAD_SETTINGS\.equals\s*\(\s*action\s*\)[\s\S]*?requestPersistedSettingsApply\s*\(\s*\)""",
            ).containsMatchIn(host),
        )

        val requestApply = declaration(host, "requestPersistedSettingsApply")
        listOf(
            "removeCallbacks(APPLY_SETTINGS_RUNNABLE)",
            "postDelayed(APPLY_SETTINGS_RUNNABLE, SETTINGS_APPLY_DEBOUNCE_MILLIS)",
        ).forEach { token ->
            assertTrue("The host coalescer must retain $token", token in requestApply)
        }

        val applySettings = declaration(host, "applyPersistedSettings")
        assertTrue(
            "RemotePreferences reads must remain inside the SystemUI failure boundary",
            Regex(
                """try\s*\{\s*String\s+fingerprint\s*=\s*persistedSettingsFingerprint""",
            ).containsMatchIn(applySettings),
        )
        assertTrue(
            "An equal persisted configuration must not republish the island",
            Regex(
                """fingerprint\.equals\s*\(\s*lastAppliedSettingsFingerprint\s*\)\s*\)\s*return""",
            ).containsMatchIn(applySettings),
        )
        assertTrue(
            "The host must record a fingerprint only after the polling action succeeds",
            Regex(
                """if\s*\([^)]*KEY_ENABLED[\s\S]*?startPolling\s*\([^)]*\)[\s\S]*?else[\s\S]*?stopPolling\s*\([^)]*\)[\s\S]*?lastAppliedSettingsFingerprint\s*=\s*fingerprint""",
            ).containsMatchIn(applySettings),
        )
        assertTrue(
            "A transient settings error must preserve an already-live polling loop",
            Regex(
                """catch\s*\(\s*Throwable[^)]*\)\s*\{[\s\S]*if\s*\(\s*pollingEnabled\s*\)\s*scheduleNextRefresh\s*\(\s*\)""",
            ).containsMatchIn(applySettings),
        )

        val focusPublisher =
            sourceFile(
                "modules/publisher-focus/src/main/kotlin/io/github/superisland/publisher/focus/FocusNotificationPublisher.kt",
            ).readText()
        val nextSequence = declaration(focusPublisher, "nextSequence")
        assertFalse(
            "Resident refreshes must not synchronously fsync sequence state on SystemUI main",
            ".commit()" in nextSequence,
        )
        assertTrue(
            "Sequence state must update in memory immediately and persist asynchronously",
            ".apply()" in nextSequence,
        )
    }

    private fun declaration(
        source: String,
        name: String,
    ): String {
        val signature =
            Regex("""(?m)^\s*[^\n{;=]*\b${Regex.escape(name)}\s*\([^\n]*""")
                .findAll(source)
                .firstOrNull { match ->
                    val prefix = match.value.substringBefore(name)
                    "fun" in prefix || " void " in " $prefix " || " static " in " $prefix "
                } ?: error("Missing declaration $name")
        val openBrace = source.indexOf('{', signature.range.first)
        require(openBrace >= 0) { "Missing body for $name" }
        var depth = 0
        var index = openBrace
        while (index < source.length) {
            when (source[index]) {
                '{' -> depth += 1
                '}' -> {
                    depth -= 1
                    if (depth == 0) return source.substring(signature.range.first, index + 1)
                }
            }
            index += 1
        }
        error("Unterminated declaration $name")
    }

    private fun invocationArguments(
        source: String,
        name: String,
    ): String {
        val invocation =
            Regex("""\b${Regex.escape(name)}\s*\(""").find(source)
                ?: error("Missing invocation $name")
        val openParenthesis = source.indexOf('(', invocation.range.first)
        var depth = 0
        var index = openParenthesis
        while (index < source.length) {
            when (source[index]) {
                '(' -> depth += 1
                ')' -> {
                    depth -= 1
                    if (depth == 0) return source.substring(openParenthesis + 1, index)
                }
            }
            index += 1
        }
        error("Unterminated invocation $name")
    }

}
