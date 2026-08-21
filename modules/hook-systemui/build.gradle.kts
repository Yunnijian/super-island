import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.library)
}

val forceMiShareDexKitScan =
    providers.gradleProperty("superIsland.dexkit.forceMiShareScan")
        .orNull
        ?.toBooleanStrictOrNull()
        ?: false
val dexKitVerification =
    configurations.create("dexKitVerification") {
        isCanBeConsumed = false
        isCanBeResolved = true
    }

@Suppress("UnstableApiUsage")
android {
    namespace = "io.github.superisland.hook.systemui"
    buildToolsVersion = "37.0.0"
    compileSdk {
        version = release(37) {
            minorApiLevel = 0
        }
    }

    defaultConfig {
        minSdk = 36
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        getByName("debug") {
            buildConfigField(
                "boolean",
                "FORCE_MISHARE_DEXKIT_SCAN",
                forceMiShareDexKitScan.toString(),
            )
        }
        getByName("release") {
            // The forced-scan switch is a development-only verification seam.
            buildConfigField("boolean", "FORCE_MISHARE_DEXKIT_SCAN", "false")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(project(":core-model"))
    implementation(project(":publisher-focus"))
    implementation(project(":source-lyric"))
    implementation(libs.hyperisland.kit)
    implementation(libs.dexkit)
    add(dexKitVerification.name, libs.dexkit)
    // Provided by the Xposed framework at runtime; never package into the APK.
    compileOnly(libs.libxposed.api)
    testImplementation(libs.junit4)
}

val hyperIslandCommit = "286bc4ce69b0924cd0ca623eb525b3b0e37afd9d"
val superIslandUpstreamsDir =
    providers.gradleProperty("superIslandUpstreamsDir")
        .orElse(providers.environmentVariable("SUPER_ISLAND_UPSTREAMS_DIR"))
        .orElse("${System.getProperty("user.home")}/.cache/super-island/upstreams")
        .map(::file)
val hyperIslandCheckout = superIslandUpstreamsDir.get().resolve("HyperIsland")
val hyperIslandRoot =
    hyperIslandCheckout.resolve("android/app/src/main/kotlin/io/github/hyperisland")
val hyperIslandGeneratedDir = layout.buildDirectory.dir("generated/source/hyperisland")
val hyperIslandAllowlistFile = layout.projectDirectory.file("hyperisland-source-allowlist.txt")
val hyperIslandAllowedSources =
    hyperIslandAllowlistFile.asFile
        .readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }

check(hyperIslandAllowedSources.size == 22) {
    "HyperIsland source allowlist must contain exactly 22 files"
}
check(hyperIslandAllowedSources.distinct().size == hyperIslandAllowedSources.size) {
    "HyperIsland source allowlist contains duplicate paths"
}
check(
    hyperIslandAllowedSources.all {
        !it.startsWith("/") && !it.contains("..") && it.endsWith(".kt")
    },
) {
    "HyperIsland source allowlist contains an unsafe path"
}

val forbiddenHyperIslandPathFragments =
    listOf(
        "ConfigManager",
        "/hook/",
        "/islanddispatch/",
        "AINotification",
        "Download",
        "/filters/",
        "StatusBar",
        "OuterGlowHook",
        "Toast",
        "network",
        "policy",
        "RootShell",
        "Abx",
    )
check(
    hyperIslandAllowedSources.none { source ->
        forbiddenHyperIslandPathFragments.any { fragment ->
            source.contains(fragment, ignoreCase = true)
        }
    },
) {
    "HyperIsland source allowlist contains a forbidden integration source"
}

val hyperIslandReferenceFiles =
    hyperIslandAllowedSources.map { relativePath -> hyperIslandRoot.resolve(relativePath) }
check(hyperIslandReferenceFiles.all { it.isFile }) {
    "HyperIsland source allowlist contains a missing file"
}

fun sha256(file: File): String =
    MessageDigest
        .getInstance("SHA-256")
        .digest(file.readBytes())
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

val dexKitVersion = "2.2.0"
val dexKitAarSha256 = "cf4488da9f721750ce5ff4311e88356cbfcb1d4444c2b519b12d8bdb5f33f2c6"
val verifyDexKitArtifact =
    tasks.register("verifyDexKitArtifact") {
        group = "verification"
        description = "Verifies the pinned DexKit AAR before it is packaged into the module."
        inputs.property("dexKitVersion", dexKitVersion)
        inputs.property("dexKitAarSha256", dexKitAarSha256)
        doLast {
            val artifacts =
                dexKitVerification
                    .resolvedConfiguration
                    .resolvedArtifacts
                    .filter { artifact ->
                        artifact.moduleVersion.id.group == "org.luckypray" &&
                            artifact.name == "dexkit" &&
                            artifact.moduleVersion.id.version == dexKitVersion
                    }
            val artifact = artifacts.singleOrNull()
                ?: error("Expected exactly one org.luckypray:dexkit:$dexKitVersion AAR")
            check(artifact.extension == "aar") {
                "DexKit dependency is not an AAR: ${artifact.file.name}"
            }
            val actual = sha256(artifact.file)
            check(actual == dexKitAarSha256) {
                "DexKit AAR changed: expected $dexKitAarSha256, found $actual"
            }
        }
    }

tasks.matching { task -> task.name == "preBuild" }.configureEach {
    dependsOn(verifyDexKitArtifact)
}

val hyperIslandPatchedSourceDigests =
    mapOf(
        "xposed/template/core/TemplateRegistry.kt" to
            "e9778fe9ed0733361869d3a820d79fdafa33254de2eea78e8565047ec41ad345",
        "xposed/template/core/models/NotifData.kt" to
            "1975de18c80c0c55070a425044b2043f88232b85900dabe8a0af60ecd161fd7e",
        "xposed/template/core/models/IslandViewModel.kt" to
            "3d1db3e9a264d2f65484a41e7c9763f024cac5a2ddb0ba587f2f2de90706e67d",
        "xposed/template/NotificationIslandNotification.kt" to
            "811f09efba4c76916165d529a21b60d219d4eb25ed4ce41b35dfba1d233df598",
        "xposed/template/renderer/IslandRenderer.kt" to
            "387e1ece2746afd013f898f70a12ed0f672bca9b76f9a3254b4ab1da23e26ad6",
        "xposed/template/renderer/image_text_with_buttons/ImageTextWithButtonsRenderer.kt" to
            "cac2a028cc7467453c4e392b571a7b6d1c8d83dc82ae00cc5ea19f4f9e273966",
        "xposed/template/renderer/image_text_with_progress/ImageTextWithProgressRenderer.kt" to
            "92614bbda5d816cfd0f29afb38d505d971b0b7bfe1277da1e95787081322a3ca",
    )

val verifyHyperIslandReference = tasks.register("verifyHyperIslandReference") {
    group = "verification"
    description = "Verifies the pinned HyperIsland checkout and restricted source allowlist."
    dependsOn(rootProject.tasks.named("verifyPinnedUpstreams"))
    inputs.file(hyperIslandAllowlistFile)
    inputs.file(hyperIslandCheckout.resolve(".git/HEAD"))
    inputs.files(hyperIslandReferenceFiles)
    inputs.property("hyperIslandCommit", hyperIslandCommit)
    doLast {
        val output =
            providers.exec {
                workingDir(hyperIslandCheckout)
                commandLine("git", "rev-parse", "HEAD")
            }.standardOutput.asText.get().trim()
        check(output == hyperIslandCommit) {
            "HyperIsland reference changed: expected $hyperIslandCommit, found $output"
        }

        val repositoryPaths =
            hyperIslandAllowedSources.map { relativePath ->
                "android/app/src/main/kotlin/io/github/hyperisland/$relativePath"
            }
        val dirtySources =
            providers.exec {
                workingDir(hyperIslandCheckout)
                commandLine(
                    listOf("git", "status", "--porcelain", "--untracked-files=no", "--") +
                        repositoryPaths,
                )
            }.standardOutput.asText.get().trim()
        check(dirtySources.isEmpty()) {
            "Pinned HyperIsland source files have local modifications:\n$dirtySources"
        }

        hyperIslandPatchedSourceDigests.forEach { (relativePath, expectedDigest) ->
            val actualDigest = sha256(hyperIslandRoot.resolve(relativePath))
            check(actualDigest == expectedDigest) {
                "HyperIsland patch input changed for $relativePath: " +
                    "expected $expectedDigest, found $actualDigest"
            }
        }
    }
}

val generateHyperIslandNotificationSource =
    tasks.register<Sync>("generateHyperIslandNotificationSource") {
        dependsOn(verifyHyperIslandReference)
        group = "build setup"
        description = "Generates the audited HyperIsland notification template subset."
        inputs.file(hyperIslandAllowlistFile)
        from(hyperIslandRoot) {
            include(*hyperIslandAllowedSources.toTypedArray())
            into("io/github/hyperisland")
        }
        into(hyperIslandGeneratedDir)

        doLast {
            val generatedPackageRoot =
                hyperIslandGeneratedDir.get().dir("io/github/hyperisland").asFile

            fun patchSource(
                relativePath: String,
                transform: (String) -> String,
            ) {
                val sourceFile = generatedPackageRoot.resolve(relativePath)
                check(sourceFile.isFile) { "Missing generated HyperIsland source: $relativePath" }
                val expectedDigest = hyperIslandPatchedSourceDigests.getValue(relativePath)
                check(sha256(sourceFile) == expectedDigest) {
                    "Generated HyperIsland patch input changed for $relativePath"
                }
                sourceFile.writeText(transform(sourceFile.readText()))
            }

            fun String.replaceExactlyOnce(
                label: String,
                before: String,
                after: String = "",
            ): String {
                val first = indexOf(before)
                check(first >= 0) { "Missing expected HyperIsland block: $label" }
                check(indexOf(before, first + before.length) < 0) {
                    "Expected exactly one HyperIsland block: $label"
                }
                return replaceRange(first, first + before.length, after)
            }

            fun String.removeLineBeforeExactlyOnce(
                label: String,
                token: String,
            ): String {
                val tokenIndex = indexOf(token)
                check(tokenIndex >= 0 && indexOf(token, tokenIndex + token.length) < 0) {
                    "Expected exactly one HyperIsland token: $label"
                }
                val tokenLineStart = lastIndexOf('\n', tokenIndex).let { if (it < 0) 0 else it + 1 }
                val previousLineStart =
                    lastIndexOf('\n', tokenLineStart - 2).let { if (it < 0) 0 else it + 1 }
                val previousLine = substring(previousLineStart, tokenLineStart).trim()
                check(previousLine.startsWith("//")) {
                    "Expected a comment immediately before HyperIsland block: $label"
                }
                return removeRange(previousLineStart, tokenLineStart)
            }

            patchSource("xposed/template/core/TemplateRegistry.kt") { original ->
                original
                    .replaceExactlyOnce(
                        "blacklist filter import",
                        "import io.github.hyperisland.xposed.template.core.filters.BlacklistFilter\n",
                    )
                    .replaceExactlyOnce(
                        "keyword filter import",
                        "import io.github.hyperisland.xposed.template.core.filters.KeywordFilter\n",
                    )
                    .replaceExactlyOnce(
                        "AI template import",
                        "import io.github.hyperisland.xposed.templates.AINotificationIslandNotification\n",
                    )
                    .replaceExactlyOnce(
                        "download template import",
                        "import io.github.hyperisland.xposed.templates.GenericDownloadIslandNotification\n",
                    )
                    .replaceExactlyOnce(
                        "template registry",
                        """    private val registry: Map<String, IslandTemplate> = listOf<IslandTemplate>(
        GenericDownloadIslandNotification,
        NotificationIslandNotification,
        AINotificationIslandNotification,
    ).associateBy { it.id }""",
                        """    private val registry: Map<String, IslandTemplate> = listOf<IslandTemplate>(
        NotificationIslandNotification,
    ).associateBy { it.id }""",
                    )
                    .replaceExactlyOnce(
                        "template filters",
                        """        val filteredData = BlacklistFilter.applyTo(context, data) ?: return
        if (KeywordFilter.shouldBlock(filteredData)) return
        template.inject(context, extras, filteredData)""",
                        "        template.inject(context, extras, data)",
                    )
                    .removeLineBeforeExactlyOnce(
                        "template filter comment",
                        "        template.inject(context, extras, data)",
                    )
                    .let { source ->
                        val forbiddenDocLine =
                            source.lineSequence().singleOrNull {
                                it.contains("GenericDownloadIslandNotification")
                            }
                        check(forbiddenDocLine != null) {
                            "Expected exactly one download fallback documentation line"
                        }
                        source.replaceExactlyOnce(
                            "download fallback documentation line",
                            "$forbiddenDocLine\n",
                        )
                    }
            }

            patchSource("xposed/template/core/models/NotifData.kt") { original ->
                original
                    .replaceExactlyOnce(
                        "notification data priority import",
                        "import android.graphics.drawable.Icon\n",
                        """import android.graphics.drawable.Icon
import io.github.superisland.model.IslandPriority
""",
                    )
                    .replaceExactlyOnce(
                        "notification data priority",
                        """    val islandEnabled: Boolean = true,
)""",
                        """    val islandEnabled: Boolean = true,
    val islandPriority: IslandPriority = IslandPriority.LOW,
)""",
                    )
            }

            patchSource("xposed/template/core/models/IslandViewModel.kt") { original ->
                original
                    .replaceExactlyOnce(
                        "island view model priority import",
                        "import android.graphics.drawable.Icon\n",
                        """import android.graphics.drawable.Icon
import io.github.superisland.model.IslandPriority
""",
                    )
                    .replaceExactlyOnce(
                        "island view model priority",
                        """    val islandEnabled: Boolean = true,
)""",
                        """    val islandEnabled: Boolean = true,
    val islandPriority: IslandPriority = IslandPriority.LOW,
)""",
                    )
            }

            patchSource("xposed/template/NotificationIslandNotification.kt") { original ->
                val withoutImportsAndBranch =
                    original
                        .replaceExactlyOnce(
                            "dispatcher import",
                            "import io.github.hyperisland.xposed.islanddispatch.IslandDispatcher\n",
                        )
                        .replaceExactlyOnce(
                            "unused module log import",
                            "import io.github.hyperisland.xposed.log\n",
                        )
                        .replaceExactlyOnce(
                            "dispatcher request import",
                            "import io.github.hyperisland.xposed.islanddispatch.IslandRequest\n",
                        )
                        .replaceExactlyOnce(
                            "status bar icon hook import",
                            "import io.github.hyperisland.xposed.hook.FocusNotifStatusBarIconHook\n",
                        )
                        .replaceExactlyOnce(
                            "dispatcher branch",
                            """        if (data.focusNotif == "off") {
            injectViaDispatcher(context, data)
            return
        }
""",
                        )

                val functionToken = "    private fun injectViaDispatcher(context: Context, data: NotifData) {"
                val functionIndex = withoutImportsAndBranch.indexOf(functionToken)
                check(
                    functionIndex >= 0 &&
                        withoutImportsAndBranch.indexOf(functionToken, functionIndex + functionToken.length) < 0,
                ) {
                    "Expected exactly one HyperIsland dispatcher function"
                }
                val sectionStart = withoutImportsAndBranch.lastIndexOf("\n    //", functionIndex)
                val nextFunctionIndex = withoutImportsAndBranch.indexOf("\n    fun process", functionIndex)
                val sectionEnd = withoutImportsAndBranch.lastIndexOf("\n    //", nextFunctionIndex)
                check(sectionStart >= 0 && nextFunctionIndex > functionIndex && sectionEnd > functionIndex) {
                    "HyperIsland dispatcher section boundaries changed"
                }
                withoutImportsAndBranch
                    .removeRange(sectionStart, sectionEnd)
                    .replaceExactlyOnce(
                        "notification priority view model",
                        "            islandEnabled = data.islandEnabled,\n",
                        """            islandEnabled = data.islandEnabled,
            islandPriority = data.islandPriority,
""",
                    )
            }

            patchSource("xposed/template/renderer/IslandRenderer.kt") { original ->
                val functionToken = "fun fixTextButtonJson(jsonParam: String): String ="
                val functionIndex = original.indexOf(functionToken)
                check(
                    functionIndex >= 0 &&
                        original.indexOf(functionToken, functionIndex + functionToken.length) < 0,
                ) { "Expected exactly one JSON repair function" }
                val commentStart = original.lastIndexOf("\n/**", functionIndex).let { index -> index + 1 }
                check(commentStart > 0 && commentStart < functionIndex) {
                    "Expected a comment before the JSON repair function"
                }
                original.replaceRange(
                    commentStart,
                    commentStart,
                    """fun forceIslandOrderFalse(jsonParam: String): String {
    val json = org.json.JSONObject(jsonParam)
    val paramV2 = json.getJSONObject("param_v2")
    val paramIsland = paramV2.optJSONObject("param_island") ?: return jsonParam
    paramIsland.put("islandOrder", false)
    return json.toString()
}

fun removeIslandParam(jsonParam: String): String {
    val json = org.json.JSONObject(jsonParam)
    val paramV2 = json.getJSONObject("param_v2")
    paramV2.remove("param_island")
    check(!paramV2.has("param_island")) { "Could not remove param_island" }
    return json.toString()
}

""",
                )
            }

            patchSource(
                "xposed/template/renderer/image_text_with_buttons/ImageTextWithButtonsRenderer.kt",
            ) { original ->
                original
                    .replaceExactlyOnce(
                        "buttons renderer status bar hook import",
                        "import io.github.hyperisland.xposed.hook.FocusNotifStatusBarIconHook\n",
                    )
                    .replaceExactlyOnce(
                        "buttons renderer ordering import",
                        "import io.github.hyperisland.xposed.renderer.fixTextButtonJson\n",
                        """import io.github.hyperisland.xposed.renderer.fixTextButtonJson
import io.github.hyperisland.xposed.renderer.forceIslandOrderFalse
import io.github.hyperisland.xposed.renderer.removeIslandParam
""",
                    )
                    .replaceExactlyOnce(
                        "buttons renderer island priority",
                        "            builder.setIslandConfig(timeout = vm.timeoutSecs)\n",
                        """            builder.setIslandConfig(
                priority = vm.islandPriority.wireValue,
                timeout = vm.timeoutSecs,
            )
""",
                    )
                    .replaceExactlyOnce(
                        "buttons renderer fixed island ordering",
                        "            extras.putString(\"miui.focus.param\", jsonParam)\n",
                        """            if (!vm.islandEnabled) jsonParam = removeIslandParam(jsonParam)
            jsonParam = forceIslandOrderFalse(jsonParam)
            extras.putString("miui.focus.param", jsonParam)
""",
                    )
                    .replaceExactlyOnce(
                        "buttons renderer status bar marker",
                        "                FocusNotifStatusBarIconHook.markDirectProxyPosted(vm.timeoutSecs)\n",
                    )
            }

            patchSource(
                "xposed/template/renderer/image_text_with_progress/ImageTextWithProgressRenderer.kt",
            ) { original ->
                original
                    .replaceExactlyOnce(
                        "progress renderer status bar hook import",
                        "import io.github.hyperisland.xposed.hook.FocusNotifStatusBarIconHook\n",
                    )
                    .replaceExactlyOnce(
                        "progress renderer ordering import",
                        "import io.github.hyperisland.xposed.renderer.fixTextButtonJson\n",
                        """import io.github.hyperisland.xposed.renderer.fixTextButtonJson
import io.github.hyperisland.xposed.renderer.forceIslandOrderFalse
import io.github.hyperisland.xposed.renderer.removeIslandParam
""",
                    )
                    .replaceExactlyOnce(
                        "progress renderer island priority",
                        "            builder.setIslandConfig(timeout = vm.timeoutSecs)\n",
                        """            builder.setIslandConfig(
                priority = vm.islandPriority.wireValue,
                timeout = vm.timeoutSecs,
            )
""",
                    )
                    .replaceExactlyOnce(
                        "progress renderer focus-only highlight",
                        "            jsonParam = injectHighlightColor(jsonParam, vm.highlightColor)\n",
                        "            if (vm.islandEnabled) jsonParam = injectHighlightColor(jsonParam, vm.highlightColor)\n",
                    )
                    .replaceExactlyOnce(
                        "progress renderer fixed island ordering",
                        "            extras.putString(\"miui.focus.param\", jsonParam)\n",
                        """            if (!vm.islandEnabled) jsonParam = removeIslandParam(jsonParam)
            jsonParam = forceIslandOrderFalse(jsonParam)
            extras.putString("miui.focus.param", jsonParam)
""",
                    )
                    .replaceExactlyOnce(
                        "progress renderer status bar marker",
                        "                FocusNotifStatusBarIconHook.markDirectProxyPosted(vm.timeoutSecs)\n",
                    )
            }

            val generatedFiles =
                generatedPackageRoot
                    .walkTopDown()
                    .filter { it.isFile }
                    .toList()
            val generatedPaths =
                generatedFiles
                    .map { it.relativeTo(generatedPackageRoot).invariantSeparatorsPath }
                    .toSet()
            check(generatedPaths == hyperIslandAllowedSources.toSet()) {
                val unexpected = generatedPaths - hyperIslandAllowedSources.toSet()
                val missing = hyperIslandAllowedSources.toSet() - generatedPaths
                "HyperIsland generated source set changed; unexpected=$unexpected, missing=$missing"
            }

            val forbiddenGeneratedReferences =
                mapOf(
                    "AI" to Regex("""(?:\bAI\b|\bAI[A-Z][A-Za-z0-9_]*)"""),
                    "Download" to Regex("Download", RegexOption.IGNORE_CASE),
                    "Dispatcher" to Regex("Dispatcher", RegexOption.IGNORE_CASE),
                    "Toast" to Regex("Toast", RegexOption.IGNORE_CASE),
                    "network" to Regex("""\bnetwork\b""", RegexOption.IGNORE_CASE),
                    "policy" to Regex("""\bpolicy\b""", RegexOption.IGNORE_CASE),
                    "RootShell" to Regex("RootShell", RegexOption.IGNORE_CASE),
                    "ABX" to Regex("""(?:\bABX\b|AbxXml)""", RegexOption.IGNORE_CASE),
                    "FocusNotifStatusBarIconHook" to Regex("FocusNotifStatusBarIconHook"),
                    "IslandOuterGlowHook" to Regex("IslandOuterGlowHook"),
                )
            val forbiddenMatches =
                buildList {
                    generatedFiles.forEach { sourceFile ->
                        val relativePath =
                            sourceFile.relativeTo(generatedPackageRoot).invariantSeparatorsPath
                        val sourceText = sourceFile.readText()
                        forbiddenGeneratedReferences.forEach { (name, pattern) ->
                            if (pattern.containsMatchIn(relativePath) || pattern.containsMatchIn(sourceText)) {
                                add("$relativePath references forbidden $name integration")
                            }
                        }
                    }
                }
            check(forbiddenMatches.isEmpty()) {
                forbiddenMatches.joinToString(
                    prefix = "Forbidden HyperIsland generated sources:\n",
                    separator = "\n",
                )
            }
        }
    }

androidComponents {
    onVariants(selector().all()) { variant ->
        variant.sources.kotlin?.addStaticSourceDirectory(
            hyperIslandGeneratedDir.get().asFile.absolutePath,
        )
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    dependsOn(generateHyperIslandNotificationSource)
}

// AGP's AAR annotation extractor also scans registered Kotlin source directories. Static source
// registration does not carry the Sync producer edge, so declare it explicitly for reproducible
// parallel builds.
tasks.matching { task ->
    task.name.startsWith("extract") && task.name.endsWith("Annotations")
}.configureEach {
    dependsOn(generateHyperIslandNotificationSource)
}
