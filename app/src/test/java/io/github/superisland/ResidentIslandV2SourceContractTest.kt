package io.github.superisland

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.readText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Source-level coverage for Android-private boundaries that have no local JVM test seam. */
class ResidentIslandV2SourceContractTest {
    @Test
    fun residentIslandStaysAboveLowPriorityWithoutRefreshReordering() {
        val residentSource =
            sourceFile(
                "modules/publisher-focus/src/main/kotlin/io/github/superisland/publisher/focus/FocusNotificationPublisher.kt",
            ).readText()
        val smartCapsuleSource =
            upstreamFile(
                "HyperIsland/android/app/src/main/kotlin/" +
                    "io/github/hyperisland/xposed/template/renderer/image_text_with_buttons/" +
                    "ImageTextWithButtonsRenderer.kt",
            ).readText()
        val versions = sourceFile("gradle/libs.versions.toml").readText()
        val residentIslandParam = functionDeclaration(residentSource, "islandParam")

        assertTrue(
            "HyperOS priority 1 is the medium island priority and must remain explicit",
            Regex("""const\s+val\s+ISLAND_PRIORITY_MEDIUM\s*=\s*1\b""")
                .containsMatchIn(residentSource),
        )
        assertTrue(
            "The resident island must outrank low-priority background islands",
            Regex("""put\(\s*"islandPriority"\s*,\s*ISLAND_PRIORITY_MEDIUM\s*\)""")
                .containsMatchIn(residentIslandParam),
        )
        assertTrue(
            "The resident island must remain an informational island",
            Regex("""put\(\s*"islandProperty"\s*,\s*ISLAND_PROPERTY_PERSISTENT\s*\)""")
                .containsMatchIn(residentIslandParam),
        )
        assertTrue(
            "Periodic resident updates must explicitly disable island-order time refresh",
            Regex("""put\(\s*"islandOrder"\s*,\s*false\s*\)""")
                .containsMatchIn(residentIslandParam),
        )
        assertTrue(
            "Smart capsules must retain HyperIsland kit's pinned priority-2 default",
            "builder.setIslandConfig(timeout = vm.timeoutSecs)" in smartCapsuleSource &&
                "hyperisland-kit = \"0.4.4\"" in versions,
        )
    }

    @Test
    fun systemUiResidentPayloadRoutesEachIconToItsOwnPictureKey() {
        val source =
            sourceFile(
                "modules/publisher-focus/src/main/kotlin/io/github/superisland/publisher/focus/FocusNotificationPublisher.kt",
            ).readText()
        val notificationBuilder = functionDeclaration(source, "buildNotification")
        val residentBuilder = functionDeclaration(source, "buildSystemUiResidentNotification")
        val focusPayload = functionDeclaration(source, "focusParamJson")
        val islandParam = functionDeclaration(source, "islandParam")
        val bigArea = functionDeclaration(source, "bigIslandArea")
        val smallArea = functionDeclaration(source, "smallIslandArea")
        val leftPictureKey = constantNameForValue(source, "io.github.superisland.focus.icon.left")
        val rightPictureKey = constantNameForValue(source, "io.github.superisland.focus.icon.right")

        assertNotEquals("Left and right pictures must use distinct Bundle keys", leftPictureKey, rightPictureKey)
        assertTrue(
            "The left picture must be stored independently",
            Regex("""putParcelable\(\s*$leftPictureKey\s*,\s*effectiveLeftIslandIcon""")
                .containsMatchIn(notificationBuilder),
        )
        assertTrue(
            "The right picture must be stored independently",
            Regex("""putParcelable\(\s*$rightPictureKey\s*,\s*effectiveRightIslandIcon""")
                .containsMatchIn(notificationBuilder),
        )

        listOf("showLeftIslandIcon", "showRightIslandIcon").forEach { parameter ->
            assertTrue(
                "The SystemUI resident builder must accept and forward $parameter",
                Regex("""\b$parameter\b""").findAll(residentBuilder).count() >= 2,
            )
        }
        assertTrue(
            "The left icon must be forwarded independently",
            Regex("""showLeftIslandIcon\s*=\s*showLeftIslandIcon""").containsMatchIn(residentBuilder),
        )
        assertTrue(
            "The right icon must be forwarded independently",
            Regex("""showRightIslandIcon\s*=\s*showRightIslandIcon""").containsMatchIn(residentBuilder),
        )

        val showSmallIconValue =
            Regex("""\.put\(\s*"showSmallIcon"\s*,\s*([A-Za-z0-9_]+)\s*\)""")
                .find(focusPayload)
                ?.groupValues
                ?.get(1)
                ?: error("focusParamJson must write param_v2.showSmallIcon")
        assertEquals("showLeftIslandIcon", showSmallIconValue)

        val smallAreaCall =
            Regex("""smallIslandArea\s*\(([^)]*)\)""")
                .find(islandParam)
                ?.value
                ?: error("The shared island payload must build smallIslandArea")
        assertTrue(
            "smallIslandArea must use the same left icon flag as param_v2.showSmallIcon",
            showSmallIconValue in smallAreaCall,
        )
        assertFalse(
            "The right icon must not change compact-island icon semantics",
            "showRightIslandIcon" in smallAreaCall,
        )

        val leftSectionStart = bigArea.indexOf("\"imageTextInfoLeft\"")
        val rightSectionStart = bigArea.indexOf("\"imageTextInfoRight\"")
        assertTrue("bigIslandArea must declare its left area", leftSectionStart >= 0)
        assertTrue("bigIslandArea must declare its right area after the left", rightSectionStart > leftSectionStart)
        val leftSection = bigArea.substring(leftSectionStart, rightSectionStart)
        val rightSection = bigArea.substring(rightSectionStart)

        assertTrue("The left area must use only its left picture key", leftPictureKey in leftSection)
        assertFalse("The left area must not consume the right picture key", rightPictureKey in leftSection)
        assertTrue("The right area must use only its right picture key", rightPictureKey in rightSection)
        assertFalse("The right area must not consume the left picture key", leftPictureKey in rightSection)
        assertTrue("The compact island must use the left picture key", leftPictureKey in smallArea)
        assertFalse("The compact island must not consume the right picture key", rightPictureKey in smallArea)
        assertTrue("The compact icon must be gated by the left flag", "showLeftIslandIcon" in smallArea)
        assertFalse("The compact icon must never be gated by the right flag", "showRightIslandIcon" in smallArea)
    }

    @Test
    fun residentStoreKeepsTheDeterministicV1ThroughV4ToV5MigrationContract() {
        val source =
            sourceFile("app/src/main/kotlin/io/github/superisland/ResidentMonitorConfigStore.kt").readText()
        val hostSync =
            sourceFile("app/src/main/kotlin/io/github/superisland/ResidentIslandHostConfigSync.kt").readText()
        val hostContract =
            sourceFile(
                "modules/publisher-focus/src/main/kotlin/io/github/superisland/publisher/focus/SystemUiResidentIslandPublisher.kt",
            ).readText()
        val schemaKey = constantNameForValue(source, "schema-version")
        val leftIconKey = constantNameForValue(source, "left-icon")
        val rightIconKey = constantNameForValue(source, "right-icon")
        val leftTitleKey = constantNameForValue(source, "left-title-metric")
        val rightTitleKey = constantNameForValue(source, "right-title-metric")
        val expandedModeKey = constantNameForValue(source, "expanded-content-mode")
        val expandedTemplateKey = constantNameForValue(source, "expanded-content-template")
        val expandedActionsKey = constantNameForValue(source, "expanded-actions")
        val legacyTitleKey = constantNameForValue(source, "title-metric")
        val legacyLeadingKindKey = constantNameForValue(source, "leading-kind")
        val legacyTrailingKindKey = constantNameForValue(source, "trailing-kind")
        val legacyTrailingMetricKey = constantNameForValue(source, "trailing-metric")

        assertTrue(
            "The resident store must declare schema version 5",
            Regex("""const\s+val\s+[A-Za-z0-9_]*SCHEMA[A-Za-z0-9_]*\s*=\s*5\b""")
                .containsMatchIn(source),
        )
        assertTrue(
            "The split-slot schema must remain identified as v2",
            Regex("""const\s+val\s+SPLIT_SLOT_SCHEMA_VERSION\s*=\s*2\b""")
                .containsMatchIn(source),
        )
        assertTrue(
            "The shared SystemUI preference contract must publish schema v5",
            Regex("""const\s+val\s+HOST_SCHEMA_VERSION\s*=\s*5\b""")
                .containsMatchIn(hostContract) &&
                "SystemUiResidentIslandContract.HOST_SCHEMA_VERSION" in hostSync,
        )

        val loadDeclaration = functionDeclaration(source, "load")
        assertTrue(
            "load() must inspect the stored schema before decoding current fields",
            Regex("""getInt\(\s*$schemaKey\s*,""").containsMatchIn(loadDeclaration),
        )
        assertTrue(
            "A v2/v3/v4 config must be decoded through the current fields before v5 normalization",
            Regex("""storedVersion\s*>=\s*SPLIT_SLOT_SCHEMA_VERSION[\s\S]*loadCurrent\(includeExpandedActions\s*=\s*false\)\.normalizedFromStorage\(\)""")
                .containsMatchIn(loadDeclaration),
        )
        assertTrue(
            "Every successful legacy load must immediately persist the migrated v5 schema",
            Regex("""\bpersist\s*\(\s*migrated\s*\)""").containsMatchIn(loadDeclaration),
        )

        val iconDecoder = functionDeclaration(source, "iconOrDefault")
        listOf("LEGACY_SUPER_ISLAND_ICON", "ResidentIslandIcon.FOLLOW_TITLE").forEach { token ->
            assertTrue("The v2 SUPER_ISLAND icon must migrate to FOLLOW_TITLE", token in iconDecoder)
        }

        val saveDeclaration = functionDeclaration(source, "save")
        assertTrue(
            "save() must normalize before using the shared v5 persistence path",
            Regex("""\bpersist\s*\(\s*normalized\s*\)""").containsMatchIn(saveDeclaration),
        )
        val persistenceDeclaration =
            functionDeclarations(source).firstOrNull { declaration ->
                Regex("""putInt\(\s*$schemaKey\s*,\s*[A-Za-z0-9_]*SCHEMA[A-Za-z0-9_]*\s*\)""")
                    .containsMatchIn(declaration)
            } ?: error("Missing the resident-config persistence function that writes schema v5")
        mapOf(
            leftIconKey to "config.leftIcon.name",
            rightIconKey to "config.rightIcon.name",
            leftTitleKey to "config.leftTitleMetric.name",
            rightTitleKey to "config.rightTitleMetric.name",
            expandedModeKey to "config.expandedContentMode.name",
            expandedTemplateKey to "config.expandedContentTemplate",
            expandedActionsKey to "ResidentExpandedActionCodec.encode(config.expandedActions)",
        ).forEach { (key, value) ->
            assertTrue(
                "The v5 persistence path must write $value under $key",
                Regex("""putString\(\s*$key\s*,\s*${Regex.escape(value)}\s*\)""")
                    .containsMatchIn(persistenceDeclaration),
            )
        }

        val legacyLoader =
            functionDeclarations(source).firstOrNull { declaration ->
                "ResidentMonitorConfig(" in declaration &&
                    legacyTitleKey in declaration &&
                    legacyLeadingKindKey in declaration &&
                    legacyTrailingKindKey in declaration &&
                    legacyTrailingMetricKey in declaration &&
                    "leftTitleMetric" in declaration &&
                    "rightTitleMetric" in declaration
            } ?: error("Missing one self-contained v1 resident-config migration function")

        val leftTitleExpression = resolveNamedArgumentExpression(legacyLoader, "leftTitleMetric")
        assertTrue("v1 title-metric must become the left title", legacyTitleKey in leftTitleExpression)

        val rightTitleExpression = resolveNamedArgumentExpression(legacyLoader, "rightTitleMetric")
        val legacyTrailingKindVariable = variableDependingOn(legacyLoader, legacyTrailingKindKey)
        listOf(
            legacyTrailingKindVariable,
            "LegacyResidentSlotKind.METRIC_SHORT",
            legacyTrailingMetricKey,
            "ResidentMetricKey.BATTERY_PERCENT",
        ).forEach { token ->
            assertTrue("The v1 right-title migration must contain $token", token in rightTitleExpression)
        }

        val leftIconExpression = resolveNamedArgumentExpression(legacyLoader, "leftIcon")
        val legacyLeadingKindVariable = variableDependingOn(legacyLoader, legacyLeadingKindKey)
        listOf(
            legacyLeadingKindVariable,
            "LegacyResidentSlotKind.NONE",
            "ResidentIslandIcon.NONE",
            "ResidentIslandIcon.FOLLOW_TITLE",
        ).forEach { token ->
            assertTrue("The v1 left-icon migration must contain $token", token in leftIconExpression)
        }

        val rightIconExpression = resolveNamedArgumentExpression(legacyLoader, "rightIcon")
        assertEquals(
            "A migrated v1 config must keep the new right icon disabled",
            "ResidentIslandIcon.NONE",
            rightIconExpression.trim(),
        )
    }

    @Test
    fun systemUiHostNormalizesOldVoltageTitlesToBatteryPercent() {
        val source =
            sourceFile(
                "modules/hook-systemui/src/main/java/io/github/superisland/hook/systemui/SystemUiResidentIslandHost.java",
            ).readText()
        val leftTitleSetting = javaMethodDeclaration(source, "leftTitleMetricSetting")
        val rightTitleSetting = javaMethodDeclaration(source, "rightTitleMetricSetting")
        val normalizer = javaMethodDeclaration(source, "normalizedTitleMetric")

        listOf("KEY_LEFT_TITLE_METRIC", "KEY_TITLE_METRIC", "normalizedTitleMetric").forEach { token ->
            assertTrue("The left title must normalize both current and old RemotePreferences", token in leftTitleSetting)
        }
        listOf(
            "KEY_RIGHT_TITLE_METRIC",
            "KEY_TRAILING_METRIC",
            "normalizedTitleMetric(metric)",
        ).forEach { token ->
            assertTrue("The right title must normalize both current and old RemotePreferences", token in rightTitleSetting)
        }
        assertFalse("Voltage is retired as an island-title choice", "case \"VOLTAGE\"" in normalizer)
        assertTrue(
            "Unsupported old title values must deterministically fall back to battery percentage",
            Regex("""default\s*:\s*return\s+"BATTERY_PERCENT"\s*;""").containsMatchIn(normalizer),
        )
    }

    @Test
    fun systemUiFanReaderUsesOnlyMiuiApiOnABoundedBackgroundExecutor() {
        val source =
            sourceFile(
                "modules/hook-systemui/src/main/java/io/github/superisland/hook/systemui/SystemUiResidentIslandHost.java",
            ).readText()
        val formatterSource =
            sourceFile(
                "modules/core-model/src/main/kotlin/io/github/superisland/model/ResidentMetricFormatter.kt",
            ).readText()
        val requestRefresh = javaMethodDeclaration(source, "requestFanRpmRefresh")
        val blockingRead = javaMethodDeclaration(source, "readFanRpmBlocking")
        val supportCheck = javaMethodDeclaration(source, "isMiuiFanSupported")
        val miChargeRead = javaMethodDeclaration(source, "readMiChargeValue")
        val requestedMiChargeKeys =
            Regex("""readMiChargeValue\("([^"]+)"\)""")
                .findAll(source)
                .map { it.groupValues[1] }
                .toSet()

        assertEquals(setOf("fan_support", "fan_real_speed"), requestedMiChargeKeys)
        assertFalse("No model-specific sysfs path may enter the generic host", "/sys/" in source)
        assertFalse("Fan target level is outside the read-only generic contract", "target_level" in source)
        assertFalse("Fan PWM is outside the read-only generic contract", "pwm_duty" in source)
        listOf("fan_support", "fan_real_speed", "miui.util.IMiCharge", "getMiChargePath").forEach { token ->
            assertTrue("The MIUI fan reader must retain $token", token in miChargeRead || token in source)
        }

        assertTrue("Fan reads must use a dedicated executor", "Executors.newSingleThreadExecutor" in source)
        assertTrue("The periodic host must submit the blocking read off-main", "FAN_READER.execute" in requestRefresh)
        assertTrue("The background task must perform the MIUI read", "readFanRpmBlocking()" in requestRefresh)
        assertTrue(
            "The host must delegate RPM validation to the shared formatter",
            "ResidentMetricFormatter.fanRpm(rpm)" in blockingRead,
        )
        assertTrue("The shared contract must cap telemetry at 50000 RPM", "MAX_FAN_RPM = 50_000" in formatterSource)
        assertTrue("Zero RPM must remain a valid stopped-fan reading", "it in 0..MAX_FAN_RPM" in formatterSource)
        assertTrue("A failed or invalid RPM read must stay unavailable", "? rpm : null" in blockingRead)
        assertTrue(
            "A parse failure must not fabricate a reading",
            Regex("""catch\s*\(NumberFormatException[^)]*\)\s*\{\s*return null;\s*}""")
                .containsMatchIn(blockingRead),
        )
        assertTrue("An unavailable support response must not claim fan support", "return false" in supportCheck)
        assertTrue("Reflection failures must remain unavailable", "return null" in miChargeRead)
        assertFalse("The MIUI failure path must never inject a fake fallback RPM", "latestFanRpm = 0" in source)
    }

    @Test
    fun fanReadCannotStarveThePeriodicSnapshotAndAlwaysClearsItsInFlightState() {
        val source =
            sourceFile(
                "modules/hook-systemui/src/main/java/io/github/superisland/hook/systemui/SystemUiResidentIslandHost.java",
            ).readText()
        val requestRefresh = javaMethodDeclaration(source, "requestFanRpmRefresh")
        val guardedPublisher =
            javaMethodDeclaration(
                source = source,
                methodName = "publishCurrentSnapshot",
                parameterFragment = "boolean refreshFanMetric",
            )

        assertTrue(
            "A completed background read must publish through the explicit non-refreshing path",
            Regex(
                """MAIN_HANDLER\.post\s*\([\s\S]*publishCurrentSnapshot\s*\(\s*context\s*,\s*false\s*\)\s*;""",
            ).containsMatchIn(requestRefresh),
        )
        assertFalse(
            "The completion callback must not re-enter the one-argument fan acquisition path",
            Regex(
                """MAIN_HANDLER\.post\s*\([\s\S]*publishCurrentSnapshot\s*\(\s*context\s*\)\s*;""",
            ).containsMatchIn(requestRefresh),
        )
        assertTrue(
            "A periodic refresh must start fan acquisition and then continue into its base snapshot",
            Regex(
                """if\s*\(\s*refreshFanMetric\s*&&\s*fanMetricRequested\s*\(\s*\)\s*\)\s*\{\s*requestFanRpmRefresh\s*\(\s*\)\s*;\s*}\s*BatterySnapshot\s+snapshot""",
            ).containsMatchIn(guardedPublisher),
        )
        assertTrue(
            "Every fan-reader outcome must clear the in-flight gate",
            Regex(
                """finally\s*\{[\s\S]*fanReadInFlight\s*=\s*false\s*;""",
            ).containsMatchIn(requestRefresh),
        )
    }

    @Test
    fun unavailableTitleMetricsUseTheSameEffectiveMetricForTextAndIcons() {
        val source =
            sourceFile(
                "modules/hook-systemui/src/main/java/io/github/superisland/hook/systemui/SystemUiResidentIslandHost.java",
            ).readText()
        val publisher =
            javaMethodDeclaration(
                source = source,
                methodName = "publishCurrentSnapshot",
                parameterFragment = "boolean refreshFanMetric",
            )
        val effectiveMetric = javaMethodDeclaration(source, "effectiveTitleMetric")
        val effectiveMetricVariables =
            Regex(
                """String\s+([A-Za-z_][A-Za-z0-9_]*)\s*=\s*effectiveTitleMetric\s*\(\s*(left|right)TitleMetricSetting\s*\(\s*\)\s*,\s*snapshot\s*\)\s*;""",
            ).findAll(publisher).associate { match ->
                match.groupValues[2] to match.groupValues[1]
            }

        assertTrue(
            "An unavailable configured title metric must fall back to battery percentage",
            Regex(
                """return\s+value\s*==\s*null\s*\|\|\s*value\.isEmpty\s*\(\s*\)\s*\?\s*"BATTERY_PERCENT"\s*:\s*normalizedMetric\s*;""",
            ).containsMatchIn(effectiveMetric),
        )
        assertEquals(
            "Both title slots must resolve one effective metric before rendering",
            setOf("left", "right"),
            effectiveMetricVariables.keys,
        )

        val leftMetric = effectiveMetricVariables.getValue("left")
        val rightMetric = effectiveMetricVariables.getValue("right")
        assertTrue(
            "The left title text must use its effective metric",
            Regex("""titleFor\s*\(\s*snapshot\s*,\s*${Regex.escape(leftMetric)}\s*\)""")
                .containsMatchIn(publisher),
        )
        assertTrue(
            "The right title text must use its effective metric",
            Regex("""shortStatusFor\s*\(\s*snapshot\s*,\s*${Regex.escape(rightMetric)}\s*\)""")
                .containsMatchIn(publisher),
        )
        assertEquals(
            "Left and right icons must follow the same effective metrics as their rendered titles",
            listOf(leftMetric, rightMetric),
            Regex("""moduleMetricIcon\s*\(\s*([A-Za-z_][A-Za-z0-9_]*)\s*\)""")
                .findAll(publisher)
                .map { it.groupValues[1] }
                .toList(),
        )
    }

    @Test
    fun residentFanTitlesKeepXiaomiSystemDefaultTextTemplate() {
        val focusSource =
            sourceFile(
                "modules/publisher-focus/src/main/kotlin/io/github/superisland/publisher/focus/FocusNotificationPublisher.kt",
            ).readText()
        val hostSource =
            sourceFile(
                "modules/hook-systemui/src/main/java/io/github/superisland/hook/systemui/SystemUiResidentIslandHost.java",
            ).readText()
        val formatterSource =
            sourceFile(
                "modules/core-model/src/main/kotlin/io/github/superisland/model/ResidentMetricFormatter.kt",
            ).readText()
        val bigArea = functionDeclaration(focusSource, "bigIslandArea")

        listOf("narrowFont", "turnAnim").forEach { override ->
            assertFalse(
                "Resident focus JSON must leave Xiaomi's default typography untouched: $override",
                override in focusSource,
            )
            assertFalse(
                "The SystemUI host must not select or forward a typography override: $override",
                override in hostSource,
            )
        }
        assertTrue(
            "The left title must remain one complete system-default title",
            Regex(
                """put\s*\(\s*"textInfo"\s*,[\s\S]*put\s*\(\s*"title"\s*,\s*title\s*\)""",
            ).containsMatchIn(bigArea),
        )
        assertTrue(
            "The right title must remain one complete system-default title",
            Regex(
                """put\s*\(\s*"title"\s*,\s*status\s*\)""",
            ).containsMatchIn(bigArea),
        )
        assertFalse(
            "The big-island template must not split RPM into a secondary content field",
            Regex("""put\s*\(\s*"content"\s*,""").containsMatchIn(bigArea),
        )
        assertTrue(
            "The shared formatter must continue publishing the complete value and RPM unit",
            Regex("""fun\s+fanRpm\([^)]*\)[\s\S]*?\.let\s*\{\s*"\${'$'}it RPM"\s*}""")
                .containsMatchIn(formatterSource),
        )
    }

    @Test
    fun everyTitleMetricHasAnOwnedDrawableAndAppHostMapping() {
        val appMapping =
            sourceFile("app/src/main/kotlin/io/github/superisland/ResidentIslandHostConfigSync.kt").readText()
        val hostSource =
            sourceFile(
                "modules/hook-systemui/src/main/java/io/github/superisland/hook/systemui/SystemUiResidentIslandHost.java",
            ).readText()
        val hostMapping = javaMethodDeclaration(hostSource, "metricIconResourceName")
        val expected =
            mapOf(
                "BATTERY_PERCENT" to "ic_resident_battery",
                "CHARGE_STATE" to "ic_resident_charging",
                "CURRENT" to "ic_resident_current",
                "POWER" to "ic_resident_power",
                "BATTERY_TEMPERATURE" to "ic_resident_temperature",
                "FAN_RPM" to "ic_resident_fan",
            )

        expected.forEach { (metric, drawable) ->
            val resource = sourceFile("app/src/main/res/drawable/$drawable.xml")
            assertTrue("$drawable must be a non-empty VectorDrawable", resource.readText().contains("<vector"))
            assertTrue("The app preference mirror must map $metric", metric in appMapping)
            assertTrue("The app preference mirror must reference $drawable", "R.drawable.$drawable" in appMapping)
            assertTrue("The SystemUI host must map $metric", "case \"$metric\"" in hostMapping)
            assertTrue("The SystemUI host must resolve $drawable", "return \"$drawable\"" in hostMapping)
        }
    }

    private fun variableDependingOn(
        declaration: String,
        dependency: String,
    ): String =
        Regex("""\bval\s+([A-Za-z_][A-Za-z0-9_]*)\s*=""")
            .findAll(declaration)
            .firstOrNull { match ->
                dependency in namedValue(declaration, match.groupValues[1], declarationKeyword = "val")
            }?.groupValues
            ?.get(1)
            ?: error("Missing local value derived from $dependency")

    private fun resolveNamedArgumentExpression(
        declaration: String,
        argument: String,
    ): String {
        val expression = namedValue(declaration, argument)
        val variable = Regex("""^[A-Za-z_][A-Za-z0-9_]*$""").matchEntire(expression.trim())?.value
        return if (variable == null) expression else namedValue(declaration, variable, declarationKeyword = "val")
    }

    private fun namedValue(
        source: String,
        name: String,
        declarationKeyword: String? = null,
    ): String {
        val prefix =
            if (declarationKeyword == null) {
                Regex("""\b${Regex.escape(name)}\s*=""")
            } else {
                Regex("""\b$declarationKeyword\s+${Regex.escape(name)}\s*=""")
            }
        val match = prefix.find(source) ?: error("Missing value for $name")
        var start = match.range.last + 1
        while (source.getOrNull(start)?.isWhitespace() == true) start += 1
        var parentheses = 0
        var braces = 0
        var brackets = 0
        var index = start
        while (index < source.length) {
            when (source[index]) {
                '(' -> parentheses += 1
                ')' -> if (parentheses == 0 && braces == 0 && brackets == 0) break else parentheses -= 1
                '{' -> braces += 1
                '}' -> if (braces == 0 && parentheses == 0 && brackets == 0) break else braces -= 1
                '[' -> brackets += 1
                ']' -> brackets -= 1
                ',' -> if (parentheses == 0 && braces == 0 && brackets == 0) break
                '\n' ->
                    if (
                        declarationKeyword != null &&
                        parentheses == 0 &&
                        braces == 0 &&
                        brackets == 0
                    ) {
                        break
                    }
            }
            index += 1
        }
        return source.substring(start, index).trim()
    }

    private fun constantNameForValue(
        source: String,
        value: String,
    ): String =
        Regex("""const\s+val\s+([A-Za-z0-9_]+)\s*=\s*"${Regex.escape(value)}"""")
            .find(source)
            ?.groupValues
            ?.get(1)
            ?: error("Missing constant for `$value`")

    private fun functionDeclaration(
        source: String,
        functionName: String,
    ): String =
        functionDeclarations(source).firstOrNull { declaration ->
            Regex("""\bfun\s+${Regex.escape(functionName)}\s*\(""").containsMatchIn(declaration)
        } ?: error("Missing function $functionName")

    private fun functionDeclarations(source: String): List<String> {
        val matches =
            Regex("""(?m)^\s*(?:(?:private|internal|public|protected)\s+)?fun\s+[A-Za-z0-9_]+\s*\(""")
                .findAll(source)
                .toList()
        return matches.mapIndexed { index, match ->
            val end = matches.getOrNull(index + 1)?.range?.first ?: source.length
            source.substring(match.range.first, end)
        }
    }

    private fun invocationArguments(
        source: String,
        functionName: String,
    ): String {
        val match =
            Regex("""\b${Regex.escape(functionName)}\s*\(""")
                .find(source)
                ?: error("Missing invocation of $functionName")
        val openParenthesis = source.indexOf('(', match.range.first)
        var depth = 0
        var index = openParenthesis
        while (index < source.length) {
            when (source[index]) {
                '(' -> depth += 1
                ')' -> {
                    depth -= 1
                    if (depth == 0) {
                        return source.substring(openParenthesis + 1, index)
                    }
                }
            }
            index += 1
        }
        error("Unterminated invocation of $functionName")
    }

    private fun javaMethodDeclaration(
        source: String,
        methodName: String,
        parameterFragment: String? = null,
    ): String {
        val signature =
            Regex("""(?m)^\s*(?:private|public|protected)\s+static\s+[^{;=]+\b${Regex.escape(methodName)}\s*\([^)]*\)\s*\{""")
                .findAll(source)
                .firstOrNull { match ->
                    parameterFragment == null || parameterFragment in match.value
                }
                ?: error("Missing Java method $methodName")
        val openBrace = source.indexOf('{', signature.range.first)
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
        error("Unterminated Java method $methodName")
    }

}
