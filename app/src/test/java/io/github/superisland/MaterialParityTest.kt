package io.github.superisland

import io.github.superisland.ui.extensionsDirectoryGroups
import io.github.superisland.ui.navigation.AppDestination
import io.github.superisland.ui.profileDirectoryGroups
import io.github.superisland.ui.superIslandDirectoryGroups
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.readText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MaterialParityTest {
    private val adaptiveRendererNames =
        linkedSetOf(
            "AppFeatureMasterSwitch",
            "AppDirectoryDetailScreen",
            "AppInformationDetailScreen",
            "FocusNotificationCapabilityScreen",
            "FocusNotificationEventScreen",
            "SmartCapsuleAppsScreen",
            "SmartCapsuleAppProfileScreen",
            "SmartCapsulePriorityPreference",
            "MediaIslandConnectionScreen",
            "MediaIslandSourcesScreen",
            "MediaIslandStatusScreen",
            "BatteryRealtimeScreen",
            "BatteryContinuousMonitorScreen",
            "BatteryMonitorEventsScreen",
            "BatteryMonitorDiagnosticsScreen",
            "ResidentMonitorConfigurationScreen",
        )

    @Test
    fun everyDestinationHasAnExplicitSharedBusinessDispatchBranch() {
        val source = sourceFile("app/src/main/kotlin/io/github/superisland/MainActivity.kt").readText()
        val dispatch =
            destinationDispatch(
                source,
                functionName = "SuperIslandDestinationContent",
                subjectName = "staticDestination",
            )
        val expected = AppDestination.entries.mapTo(linkedSetOf()) { it.name }

        assertFalse(
            "The shared destination dispatcher must stay exhaustive; an else branch can hide a newly added route",
            dispatch.hasElseBranch,
        )
        assertEquals(
            "Every AppDestination must have its own business dispatch branch",
            expected,
            dispatch.destinations,
        )

        val function = functionBlock(source, "SuperIslandDestinationContent")
        listOf(
            "SmartCapsuleChannelsDestination",
            "SmartCapsuleChannelDetailDestination",
        ).forEach { routeName ->
            assertTrue(
                "$routeName must have an explicit shared business dispatch branch",
                Regex("""\bis\s+$routeName\s*->""").containsMatchIn(function),
            )
        }
    }

    @Test
    fun materialDetailsCannotUseAParallelOrMigrationDispatcher() {
        val uiSources = productionKotlinSources().joinToString(separator = "\n") { it.readText() }
        val structuralSource = maskCommentsAndLiterals(uiSources)
        val forbiddenSymbols =
            listOf(
                "MaterialDestinationContent",
                "MaterialMigrationDetailPage",
            )

        forbiddenSymbols.forEach { symbol ->
            assertFalse(
                "Material details must use the shared business dispatcher, not `$symbol`",
                Regex("""\b$symbol\b""").containsMatchIn(structuralSource),
            )
        }
        assertFalse(
            "Material pages must not expose migration placeholders",
            sourceFile("app/src/main/kotlin/io/github/superisland/ui/material")
                .let { materialRoot ->
                    Files.walk(materialRoot).use { paths ->
                        paths
                            .filter { path -> Files.isRegularFile(path) && path.fileName.toString().endsWith(".kt") }
                            .anyMatch { path -> "正在迁移" in path.readText() || "迁移占位" in path.readText() }
                    }
                },
        )
    }

    @Test
    fun featureRenderersAreImportedAndDispatchedOnlyThroughTheAdaptiveLayer() {
        val sourceRoot = sourceFile("app/src/main/kotlin/io/github/superisland")
        val mainActivity = maskCommentsAndLiterals(sourceRoot.resolve("MainActivity.kt").readText())
        val adaptive =
            maskCommentsAndLiterals(sourceRoot.resolve("ui/adaptive/AdaptiveFeatureScreens.kt").readText())
        val material =
            maskCommentsAndLiterals(sourceRoot.resolve("ui/material/MaterialFeatureScreens.kt").readText())
        val materialRendererNames =
            Regex("""(?m)^fun\s+Material([A-Za-z0-9_]+)\s*\(""")
                .findAll(material)
                .mapTo(linkedSetOf()) { match -> match.groupValues[1] }

        assertEquals(
            "Every public Material feature renderer must have one adaptive wrapper",
            adaptiveRendererNames,
            materialRendererNames,
        )

        adaptiveRendererNames.forEach { renderer ->
            assertTrue(
                "MainActivity must import $renderer from ui.adaptive",
                Regex(
                    """(?m)^import\s+io\.github\.superisland\.ui\.adaptive\.$renderer\s*$""",
                ).containsMatchIn(mainActivity),
            )
            assertFalse(
                "MainActivity must not bypass ui.adaptive for $renderer",
                Regex(
                    """(?m)^import\s+io\.github\.superisland\.design\.$renderer(?:\s+as\s+\w+)?\s*$""",
                ).containsMatchIn(mainActivity),
            )
            assertTrue(
                "$renderer must import the Miuix renderer through an explicit alias",
                Regex(
                    """(?m)^import\s+io\.github\.superisland\.design\.$renderer\s+as\s+Miuix$renderer\s*$""",
                ).containsMatchIn(adaptive),
            )
            assertTrue(
                "$renderer must import its Material renderer",
                Regex(
                    """(?m)^import\s+io\.github\.superisland\.ui\.material\.Material$renderer\s*$""",
                ).containsMatchIn(adaptive),
            )

            val wrapperBody = functionBody(adaptive, renderer)
            assertTrue(
                "$renderer must dispatch from LocalUiMode",
                Regex("""\bwhen\s*\(\s*LocalUiMode\.current\s*\)""").containsMatchIn(wrapperBody),
            )
            assertTrue(
                "$renderer must handle UiMode.Miuix",
                Regex("""\bUiMode\.Miuix\b""").containsMatchIn(wrapperBody),
            )
            assertTrue(
                "$renderer must handle UiMode.Material",
                Regex("""\bUiMode\.Material\b""").containsMatchIn(wrapperBody),
            )
            assertTrue(
                "$renderer must invoke its Miuix renderer",
                Regex("""\bMiuix$renderer\s*\(""").containsMatchIn(wrapperBody),
            )
            assertTrue(
                "$renderer must invoke its Material renderer",
                Regex("""\bMaterial$renderer\s*\(""").containsMatchIn(wrapperBody),
            )
            assertFalse(
                "$renderer must keep UiMode dispatch exhaustive",
                Regex("""\belse\s*->""").containsMatchIn(wrapperBody),
            )
        }
    }

    @Test
    fun smartCapsuleSystemAppFilterIsSharedAcrossBothSkins() {
        val main = sourceFile("app/src/main/kotlin/io/github/superisland/MainActivity.kt").readText()
        val stateOwner =
            sourceFile("app/src/main/kotlin/io/github/superisland/SmartCapsuleDashboardStateOwner.kt").readText()
        val adaptive =
            functionBlock(
                sourceFile("app/src/main/kotlin/io/github/superisland/ui/adaptive/AdaptiveFeatureScreens.kt").readText(),
                "SmartCapsuleAppsScreen",
            )
        val miuix =
            sourceFile(
                "modules/ui-design-system/src/main/kotlin/io/github/superisland/design/KernelSuSmartCapsuleAppsMiuix.kt",
            ).readText()
        val material =
            sourceFile(
                "app/src/main/kotlin/io/github/superisland/ui/material/KernelSuSmartCapsuleAppsMaterial.kt",
            ).readText()
        val directory =
            sourceFile("app/src/main/kotlin/io/github/superisland/SmartCapsuleAppDirectory.kt").readText()

        assertTrue(
            "The root-stable owner must own and persist the filter state",
            "val showSystemApps: Boolean" in stateOwner &&
                "appListPreferences.setShowSystemApps(show)" in stateOwner &&
                "val showSystemApps = dashboardState.showSystemApps" in main,
        )
        assertTrue(
            "The root-stable owner must build directory rows before skin dispatch",
            "buildSmartCapsuleDirectoryUiModel(" in stateOwner &&
                "val appOptions = dashboardState.appOptions" in main &&
                "filterSmartCapsuleAppEntries(" in directory,
        )
        assertTrue(
            "Navigation destinations must share one root-stable state owner",
            "val smartCapsuleStateOwner" in main && "stateOwner = smartCapsuleStateOwner" in main,
        )
        listOf("showSystemApps", "onToggleSystemApps").forEach { parameter ->
            val identifier = Regex("""\b$parameter\b""")
            assertTrue("The adaptive layer must forward $parameter to both skins", identifier.findAll(adaptive).count() >= 3)
            assertTrue("Miuix must consume $parameter", identifier.findAll(miuix).count() >= 2)
            assertTrue("Material must consume $parameter", identifier.findAll(material).count() >= 2)
        }
        assertTrue("Miuix must use KernelSU's native list popup", "OverlayListPopup" in miuix)
        assertTrue("Material must use its native dropdown menu", "DropdownMenuPopup" in material)
        assertTrue("Miuix must lazily compose the installed-app directory", "LazyColumn(" in miuix)
        assertTrue("Material must lazily compose the installed-app directory", "LazyColumn(" in material)
        assertTrue("Miuix rows must use package-name keys", "key = NotificationSourceOptionUi::id" in miuix)
        assertTrue("Material rows must use package-name keys", "key = { _, app -> app.id }" in material)
        assertTrue("Miuix rows need a stable content type", "contentType = { \"smart-capsule-app\" }" in miuix)
        assertTrue(
            "Miuix must use KernelSU's indicated app card row",
            "SmartCapsuleAppRow(" in miuix && "showIndication = true" in miuix,
        )
        assertFalse("Miuix app rows must not add a card per package", "AppSelectableInfoCard(" in miuix)
        assertTrue("Material must use KernelSU's segmented list row", "SegmentedListItem(" in material)
        assertFalse(
            "Material app rows must not allocate a one-row SegmentedColumn wrapper",
            "MaterialSelectableInformationGroup(" in material,
        )
    }

    @Test
    fun residentIslandConfigurationHasTheSameV2ContractInBothSkins() {
        val adaptiveBlock =
            functionBlock(
                sourceFile("app/src/main/kotlin/io/github/superisland/ui/adaptive/AdaptiveFeatureScreens.kt").readText(),
                "ResidentMonitorConfigurationScreen",
            )
        val miuixBlock =
            functionBlock(
                sourceFile("modules/ui-design-system/src/main/kotlin/io/github/superisland/design/Screens.kt").readText(),
                "ResidentMonitorConfigurationScreen",
            )
        val materialBlock =
            functionBlock(
                sourceFile("app/src/main/kotlin/io/github/superisland/ui/material/MaterialFeatureScreens.kt").readText(),
                "MaterialResidentMonitorConfigurationScreen",
            )
        val sharedParameters =
            listOf(
                "leftIconOptions",
                "rightIconOptions",
                "leftTitleOptions",
                "rightTitleOptions",
                "onSelectLeftIcon",
                "onSelectRightIcon",
                "onSelectLeftTitle",
                "onSelectRightTitle",
            )

        sharedParameters.forEach { parameter ->
            val identifier = Regex("""\b$parameter\b""")
            assertTrue(
                "The adaptive resident-island wrapper must accept and forward $parameter to both skins",
                identifier.findAll(adaptiveBlock).count() >= 3,
            )
            assertTrue(
                "The Miuix resident-island screen must consume $parameter",
                identifier.findAll(miuixBlock).count() >= 2,
            )
            assertTrue(
                "The Material resident-island screen must consume $parameter",
                identifier.findAll(materialBlock).count() >= 2,
            )
        }

        val retiredParameters =
            listOf(
                "titleMetricOptions",
                "leadingOptions",
                "trailingOptions",
                "onSelectTitleMetric",
                "onSelectLeading",
                "onSelectTrailing",
            )
        retiredParameters.forEach { parameter ->
            listOf(adaptiveBlock, miuixBlock, materialBlock).forEach { block ->
                assertFalse(
                    "The v2 resident-island renderer must not retain $parameter",
                    Regex("""\b$parameter\b""").containsMatchIn(block),
                )
            }
        }

        val requiredLabels =
            listOf(
                "常驻超级岛",
                "超级岛图标",
                "左侧图标",
                "右侧图标",
                "显示内容",
                "左侧岛标题",
                "右侧岛标题",
                "标题刷新间隔",
                "展开内容",
            )
        val retiredLabels =
            listOf(
                "常驻系统状态",
                "SystemUI 独立刷新",
                "胶囊图标",
                "胶囊标题",
                "胶囊布局",
                "右侧显示",
            )
        listOf("Miuix" to miuixBlock, "Material" to materialBlock).forEach { (skin, block) ->
            requiredLabels.forEach { label ->
                assertTrue("$skin must expose the resident-island label `$label`", label in block)
            }
            retiredLabels.forEach { label ->
                assertFalse(
                    "$skin must remove the retired resident-island label `$label`",
                    Regex(""""${Regex.escape(label)}"""").containsMatchIn(block),
                )
            }
        }
    }

    @Test
    fun sectionTitlesUseEachSkinsNativeComponent() {
        val miuixDirectory =
            functionBlock(
                sourceFile("modules/ui-design-system/src/main/kotlin/io/github/superisland/design/Directory.kt").readText(),
                "DirectoryGroup",
            )
        val miuixResident =
            functionBlock(
                sourceFile("modules/ui-design-system/src/main/kotlin/io/github/superisland/design/Screens.kt").readText(),
                "ResidentMonitorConfigurationScreen",
            )
        val miuixExpanded =
            functionBlock(
                sourceFile(
                    "modules/ui-design-system/src/main/kotlin/io/github/superisland/design/ResidentExpandedContentEditor.kt",
                ).readText(),
                "ResidentExpandedContentEditorMiuix",
            )
        val miuixSmartCapsuleProfile =
            sourceFile(
                "modules/ui-design-system/src/main/kotlin/io/github/superisland/design/KernelSuSmartCapsuleAppProfileMiuix.kt",
            ).readText()
        val materialResident =
            functionBlock(
                sourceFile(
                    "app/src/main/kotlin/io/github/superisland/ui/material/MaterialFeatureScreens.kt",
                ).readText(),
                "MaterialResidentMonitorConfigurationScreen",
            )
        val materialExpanded =
            functionBlock(
                sourceFile(
                    "app/src/main/kotlin/io/github/superisland/ui/material/MaterialResidentExpandedContentScreen.kt",
                ).readText(),
                "MaterialResidentExpandedContentEditor",
            )
        val materialSmartCapsuleProfile =
            functionBlock(
                sourceFile(
                    "app/src/main/kotlin/io/github/superisland/ui/material/" +
                        "KernelSuSmartCapsuleAppProfileMaterial.kt",
                ).readText(),
                "KernelSuSmartCapsuleAppProfileMaterial",
            )
        val materialPriority =
            functionBlock(
                sourceFile(
                    "app/src/main/kotlin/io/github/superisland/ui/material/" +
                        "KernelSuSmartCapsuleAppProfileMaterial.kt",
                ).readText(),
                "PriorityButtonGroup",
            )

        assertTrue(
            "Miuix directory groups must use the library SmallTitle component",
            "SmallTitle(" in miuixDirectory && "text = group.title" in miuixDirectory,
        )
        val compactMiuixTitleMargin =
            "insideMargin = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 0.dp)"
        assertTrue(
            "Miuix directory titles must keep the compact card-attached layout",
            compactMiuixTitleMargin in miuixDirectory,
        )
        listOf("常驻超级岛", "超级岛图标", "显示内容").forEach { title ->
            assertTrue(
                "Miuix resident section `$title` must use SmallTitle",
                "text = \"$title\"," in miuixResident,
            )
            assertFalse(
                "Miuix resident section `$title` must not fall back to a bare Text",
                "Text(\"$title\")" in miuixResident,
            )
        }
        assertEquals(
            "Every Miuix resident title must use the same compact component margin",
            3,
            Regex(Regex.escape(compactMiuixTitleMargin)).findAll(miuixResident).count(),
        )
        listOf("占位符（点击插入光标位置）", "预览", "展开卡片快捷按钮").forEach { title ->
            assertTrue(
                "Miuix expanded-content section `$title` must use SmallTitle",
                "text = \"$title\"," in miuixExpanded,
            )
            assertFalse(
                "Miuix expanded-content section `$title` must not fall back to a bare Text",
                "Text(\"$title\")" in miuixExpanded,
            )
        }
        assertEquals(
            "Every Miuix expanded-content title must use the same compact component margin",
            3,
            Regex(Regex.escape(compactMiuixTitleMargin)).findAll(miuixExpanded).count(),
        )
        assertTrue(
            "The Miuix Channel title must share the compact SmallTitle layout",
            "text = \"通知 Channel\"" in miuixSmartCapsuleProfile &&
                compactMiuixTitleMargin in miuixSmartCapsuleProfile,
        )

        listOf("常驻超级岛", "超级岛图标", "显示内容").forEach { title ->
            assertTrue(
                "Material resident section `$title` must use SegmentedColumn's title slot",
                "title = \"$title\"" in materialResident,
            )
            assertFalse(
                "Material resident section `$title` must not fall back to a bare Text",
                "Text(\"$title\")" in materialResident,
            )
        }
        listOf("占位符（点击插入光标位置）", "预览", "展开卡片快捷按钮").forEach { title ->
            assertTrue(
                "Material expanded-content section `$title` must use SegmentedColumn's title slot",
                "title = \"$title\"" in materialExpanded,
            )
            assertFalse(
                "Material expanded-content section `$title` must not fall back to a bare Text",
                "Text(\"$title\")" in materialExpanded,
            )
        }
        assertTrue(
            "Material app priority must use SegmentedColumn's title slot",
            "SegmentedColumn(" in materialPriority && "title = \"默认岛优先级\"" in materialPriority,
        )
        assertFalse(
            "Material app priority must not use a bare Text title",
            "Text(\"默认岛优先级\")" in materialPriority,
        )
        assertTrue(
            "Material Channel sections must use SegmentedColumn's title slot",
            Regex("""title\s*=\s*"通知 Channel"""")
                .findAll(materialSmartCapsuleProfile)
                .count() == 2,
        )
        assertFalse(
            "Material Channel sections must not use a bare Text title",
            "Text(\"通知 Channel\")" in materialSmartCapsuleProfile,
        )
    }

    @Test
    fun smartCapsuleFocusOnlyStateAndCallbackAreSharedAcrossBothSkins() {
        val adaptive =
            functionBlock(
                sourceFile(
                    "app/src/main/kotlin/io/github/superisland/ui/adaptive/AdaptiveFeatureScreens.kt",
                ).readText(),
                "SmartCapsuleAppProfileScreen",
            )
        val miuix =
            functionBlock(
                sourceFile(
                    "modules/ui-design-system/src/main/kotlin/io/github/superisland/design/" +
                        "KernelSuSmartCapsuleAppProfileMiuix.kt",
                ).readText(),
                "KernelSuSmartCapsuleAppProfileMiuix",
            )
        val material =
            functionBlock(
                sourceFile(
                    "app/src/main/kotlin/io/github/superisland/ui/material/" +
                        "KernelSuSmartCapsuleAppProfileMaterial.kt",
                ).readText(),
                "KernelSuSmartCapsuleAppProfileMaterial",
            )
        val callback = Regex("""\bonFocusOnlyChange\b""")

        assertTrue(
            "The adaptive profile must accept and forward focus-only changes to both skins",
            callback.findAll(adaptive).count() >= 3,
        )
        listOf("Miuix" to miuix, "Material" to material).forEach { (skin, source) ->
            assertTrue("$skin must consume the shared focus-only state", "state.focusOnly" in source)
            assertTrue("$skin must forward focus-only changes", callback.findAll(source).count() >= 2)
            assertTrue("$skin must expose the focus-only control", "仅显示焦点通知" in source)
        }
        assertTrue(
            "Material must keep the master switch before its dependent focus-only switch",
            material.indexOf("title = \"超级岛通知\"") <
                material.indexOf("title = \"仅显示焦点通知\""),
        )
    }

    @Test
    fun bothSkinsConsumeTheSamePrimaryDirectoryModels() {
        val sourceRoot = sourceFile("app/src/main/kotlin")
        val materialSource =
            maskCommentsAndLiterals(sourceRoot.resolve("io/github/superisland/ui/material/MaterialPages.kt").readText())
        val sharedModelSource =
            maskCommentsAndLiterals(sourceRoot.resolve("io/github/superisland/ui/PrimaryDirectoryModels.kt").readText())
        val allProductionSource =
            maskCommentsAndLiterals(productionKotlinSources().joinToString(separator = "\n") { it.readText() })
        val sharedDirectories =
            listOf(
                SharedDirectory(
                    factory = "superIslandDirectoryGroups",
                    miuixSource = "io/github/superisland/ui/superisland/SuperIslandMiuix.kt",
                ),
                SharedDirectory(
                    factory = "extensionsDirectoryGroups",
                    miuixSource = "io/github/superisland/ui/extensions/ExtensionsMiuix.kt",
                ),
                SharedDirectory(
                    factory = "profileDirectoryGroups",
                    miuixSource = "io/github/superisland/ui/profile/ProfileMiuix.kt",
                ),
            )

        sharedDirectories.forEach { directory ->
            val declaration = Regex("""\bfun\s+${directory.factory}\s*\(""")
            val call = Regex("""\b${directory.factory}\s*\(\s*\)""")
            val miuixSource = maskCommentsAndLiterals(sourceRoot.resolve(directory.miuixSource).readText())

            assertEquals(
                "${directory.factory} must have one source of truth",
                1,
                declaration.findAll(allProductionSource).count(),
            )
            assertTrue("The shared model must declare ${directory.factory}", declaration.containsMatchIn(sharedModelSource))
            assertTrue("Miuix must consume ${directory.factory}", call.containsMatchIn(miuixSource))
            assertTrue("Material must consume ${directory.factory}", call.containsMatchIn(materialSource))
        }

        val skinSources =
            buildString {
                append(materialSource)
                sharedDirectories.forEach { directory ->
                    append(maskCommentsAndLiterals(sourceRoot.resolve(directory.miuixSource).readText()))
                }
            }
        assertFalse(
            "Skin renderers must not define private directory entries beside the shared models",
            Regex("""\bDirectory(?:Entry|Group)Ui\s*\(""").containsMatchIn(skinSources),
        )
    }

    @Test
    fun entryIdsRemainUniqueInsideEachSharedDirectory() {
        val directories =
            mapOf(
                "superIslandDirectoryGroups" to superIslandDirectoryGroups(),
                "extensionsDirectoryGroups" to extensionsDirectoryGroups(),
                "profileDirectoryGroups" to profileDirectoryGroups(),
            )

        directories.forEach { (name, groups) ->
            val ids = groups.flatMap { group -> group.entries }.map { entry -> entry.id }
            assertEquals("$name entry ids must be unique", ids.size, ids.distinct().size)
        }
    }

    @Test
    fun skinSpecificKernelSuCapabilitiesStayInTheirUpstreamRenderer() {
        val generatedRoot =
            sourceFile(
                "app/build/generated/source/kernelsuMaterial/me/weishu/kernelsu/ui/screen/colorpalette",
            )
        val materialTheme = generatedRoot.resolve("ColorPaletteScreenMaterial.kt").readText()
        val miuixTheme = generatedRoot.resolve("ColorPaletteScreenMiuix.kt").readText()
        val materialShell =
            sourceFile("app/src/main/kotlin/io/github/superisland/ui/material/MaterialPages.kt").readText()
        val materialPrimaryShell =
            functionBody(maskCommentsAndLiterals(materialShell), "MaterialPrimaryPagerScaffold")
        val miuixShell =
            sourceFile(
                "modules/ui-design-system/src/main/kotlin/io/github/superisland/design/KernelSuMiuixPrimaryPagerScaffold.kt",
            ).readText()

        listOf(
            "settings_enable_blur",
            "settings_floating_bottom_bar",
            "settings_enable_glass",
            "settings_navigation_badge",
        ).forEach { token ->
            assertFalse("KernelSU Material must not expose $token", materialTheme.contains(token))
        }
        listOf(
            "settings_enable_blur",
            "settings_floating_bottom_bar",
            "settings_enable_glass",
        ).forEach { token ->
            assertTrue("KernelSU Miuix must retain $token", miuixTheme.contains(token))
        }
        assertFalse(
            "The product removed the non-actionable navigation badge",
            miuixTheme.contains("settings_navigation_badge"),
        )
        assertFalse(
            "Material must keep KernelSU's standard bottom bar",
            materialPrimaryShell.contains("enableFloatingBottomBar"),
        )
        assertTrue("Miuix must retain KernelSU's floating bottom bar", miuixShell.contains("enableFloatingBottomBar"))
        listOf("navigationBadgeCount", "showNavigationBadge", "BadgedBox").forEach { token ->
            assertFalse("Material shell must not render $token", materialPrimaryShell.contains(token))
            assertFalse("Miuix shell must not render $token", miuixShell.contains(token))
        }
    }

    private data class SharedDirectory(
        val factory: String,
        val miuixSource: String,
    )

    private data class DestinationDispatch(
        val destinations: Set<String>,
        val hasElseBranch: Boolean,
    )

    private fun destinationDispatch(
        source: String,
        functionName: String,
        subjectName: String = "destination",
    ): DestinationDispatch {
        val masked = maskCommentsAndLiterals(source)
        val functionBody = functionBody(masked, functionName)
        val whenMatch =
            Regex("""\bwhen\s*\(\s*${Regex.escape(subjectName)}\s*\)\s*\{""")
                .find(functionBody)
                ?: error("Missing when ($subjectName) in $functionName")
        val whenOpenBrace = functionBody.indexOf('{', startIndex = whenMatch.range.first)
        val whenCloseBrace = matchingBrace(functionBody, whenOpenBrace)
        val whenBody = functionBody.substring(whenOpenBrace + 1, whenCloseBrace)

        var braceDepth = 0
        var parenthesisDepth = 0
        var bracketDepth = 0
        var hasElseBranch = false
        val destinations = linkedSetOf<String>()
        var index = 0
        while (index < whenBody.lastIndex) {
            when (whenBody[index]) {
                '{' -> braceDepth += 1
                '}' -> braceDepth -= 1
                '(' -> parenthesisDepth += 1
                ')' -> parenthesisDepth -= 1
                '[' -> bracketDepth += 1
                ']' -> bracketDepth -= 1
                '-' ->
                    if (
                        whenBody[index + 1] == '>' &&
                        braceDepth == 0 &&
                        parenthesisDepth == 0 &&
                        bracketDepth == 0
                    ) {
                        val prefix = whenBody.substring(0, index)
                        val labelSuffix =
                            Regex(
                                """(?s)((?:AppDestination\.[A-Z0-9_]+\s*,\s*)*AppDestination\.[A-Z0-9_]+\s*)$""",
                            ).find(prefix)
                        if (labelSuffix != null) {
                            Regex("""AppDestination\.([A-Z0-9_]+)""")
                                .findAll(labelSuffix.value)
                                .mapTo(destinations) { it.groupValues[1] }
                        } else if (Regex("""(?s)\belse\s*$""").containsMatchIn(prefix)) {
                            hasElseBranch = true
                        }
                    }
            }
            index += 1
        }

        return DestinationDispatch(destinations = destinations, hasElseBranch = hasElseBranch)
    }

    private fun functionBody(
        maskedSource: String,
        functionName: String,
    ): String {
        val functionMatch =
            Regex("""\bfun\s+$functionName\s*\(""").find(maskedSource)
                ?: error("Missing $functionName")
        val functionOpenBrace = maskedSource.indexOf('{', startIndex = functionMatch.range.last + 1)
        require(functionOpenBrace >= 0) { "Missing body for $functionName" }
        val functionCloseBrace = matchingBrace(maskedSource, functionOpenBrace)
        return maskedSource.substring(functionOpenBrace + 1, functionCloseBrace)
    }

    private fun functionBlock(
        source: String,
        functionName: String,
    ): String {
        val maskedSource = maskCommentsAndLiterals(source)
        val functionMatch =
            Regex("""\bfun\s+$functionName\s*\(""").find(maskedSource)
                ?: error("Missing $functionName")
        val functionOpenBrace = maskedSource.indexOf('{', startIndex = functionMatch.range.last + 1)
        require(functionOpenBrace >= 0) { "Missing body for $functionName" }
        val functionCloseBrace = matchingBrace(maskedSource, functionOpenBrace)
        return source.substring(functionMatch.range.first, functionCloseBrace + 1)
    }

    private fun matchingBrace(
        source: String,
        openBrace: Int,
    ): Int {
        var depth = 0
        for (index in openBrace until source.length) {
            when (source[index]) {
                '{' -> depth += 1
                '}' -> {
                    depth -= 1
                    if (depth == 0) return index
                }
            }
        }
        error("Unbalanced braces")
    }

    /** Preserves indexes and Kotlin punctuation while hiding braces/arrows inside text or comments. */
    private fun maskCommentsAndLiterals(source: String): String {
        val result = StringBuilder(source.length)
        var state = LexicalState.CODE
        var index = 0
        var blockCommentDepth = 0
        while (index < source.length) {
            val char = source[index]
            val next = source.getOrNull(index + 1)
            val third = source.getOrNull(index + 2)
            when (state) {
                LexicalState.CODE ->
                    when {
                        char == '/' && next == '/' -> {
                            result.append("  ")
                            index += 2
                            state = LexicalState.LINE_COMMENT
                        }
                        char == '/' && next == '*' -> {
                            result.append("  ")
                            index += 2
                            blockCommentDepth = 1
                            state = LexicalState.BLOCK_COMMENT
                        }
                        char == '"' && next == '"' && third == '"' -> {
                            result.append("   ")
                            index += 3
                            state = LexicalState.TRIPLE_STRING
                        }
                        char == '"' -> {
                            result.append(' ')
                            index += 1
                            state = LexicalState.STRING
                        }
                        char == '\'' -> {
                            result.append(' ')
                            index += 1
                            state = LexicalState.CHAR
                        }
                        else -> {
                            result.append(char)
                            index += 1
                        }
                    }
                LexicalState.LINE_COMMENT -> {
                    result.append(if (char == '\n') '\n' else ' ')
                    index += 1
                    if (char == '\n') state = LexicalState.CODE
                }
                LexicalState.BLOCK_COMMENT ->
                    when {
                        char == '/' && next == '*' -> {
                            result.append("  ")
                            index += 2
                            blockCommentDepth += 1
                        }
                        char == '*' && next == '/' -> {
                            result.append("  ")
                            index += 2
                            blockCommentDepth -= 1
                            if (blockCommentDepth == 0) state = LexicalState.CODE
                        }
                        else -> {
                            result.append(if (char == '\n') '\n' else ' ')
                            index += 1
                        }
                    }
                LexicalState.STRING,
                LexicalState.CHAR,
                -> {
                    val terminator = if (state == LexicalState.STRING) '"' else '\''
                    when {
                        char == '\\' && next != null -> {
                            result.append("  ")
                            index += 2
                        }
                        char == terminator -> {
                            result.append(' ')
                            index += 1
                            state = LexicalState.CODE
                        }
                        else -> {
                            result.append(if (char == '\n') '\n' else ' ')
                            index += 1
                        }
                    }
                }
                LexicalState.TRIPLE_STRING ->
                    if (char == '"' && next == '"' && third == '"') {
                        result.append("   ")
                        index += 3
                        state = LexicalState.CODE
                    } else {
                        result.append(if (char == '\n') '\n' else ' ')
                        index += 1
                    }
            }
        }
        return result.toString()
    }

    private fun productionKotlinSources(): List<Path> {
        val root = sourceFile("app/src/main/kotlin")
        return Files.walk(root).use { paths ->
            paths
                .filter { path -> Files.isRegularFile(path) && path.fileName.toString().endsWith(".kt") }
                .sorted()
                .toList()
        }
    }

    private enum class LexicalState {
        CODE,
        LINE_COMMENT,
        BLOCK_COMMENT,
        STRING,
        TRIPLE_STRING,
        CHAR,
    }
}
