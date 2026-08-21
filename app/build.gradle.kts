import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipFile
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

@Suppress("UnstableApiUsage")
android {
    namespace = "io.github.superisland"
    buildToolsVersion = "37.0.0"
    compileSdk {
        version = release(37) {
            minorApiLevel = 0
        }
    }

    defaultConfig {
        applicationId = "io.github.superisland"
        minSdk = 36
        targetSdk = 36
        versionCode = 12
        versionName = "0.4.8-m4-dev"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
            )
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(project(":core-model"))
    implementation(project(":hook-systemui"))
    implementation(project(":publisher-focus"))
    implementation(project(":source-lyric"))
    implementation(project(":source-notification"))
    implementation(project(":source-root"))
    implementation(project(":source-screenrecord"))
    implementation(project(":ui-design-system"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.compose.foundation)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.material.kolor)
    // KernelSU's navigation stack: Miuix NavDisplay supplies the same page-transition contract.
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.miuix.blur)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.navigation3.ui)
    // compileOnly dependencies do not propagate from :hook-systemui. Keep the API on the final
    // R8 library classpath so externally invoked Hooker implementations retain their ABI.
    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.service)
    implementation(libs.hiddenapibypass)
    implementation(libs.appiconloader)
    testImplementation(libs.junit4)
}

// The color-palette pages, themes, and visual components are compiled directly from KernelSU
// Manager at the fixed GPL-3.0-or-later reference commit. Resources and KernelSU's optional WebUI
// CSS bridge are rebound for this Android application. The module-count navigation badge is
// removed at the generated boundary because this product has no equivalent actionable count.
val kernelSuMaterialGeneratedDir = layout.buildDirectory.dir("generated/source/kernelsuMaterial")
val superIslandUpstreamsDir =
    providers.gradleProperty("superIslandUpstreamsDir")
        .orElse(providers.environmentVariable("SUPER_ISLAND_UPSTREAMS_DIR"))
        .orElse("${System.getProperty("user.home")}/.cache/super-island/upstreams")
        .map(::file)
val generateKernelSuMaterialThemeSource = tasks.register<Sync>("generateKernelSuMaterialThemeSource") {
    dependsOn(":ui-design-system:verifyKernelSuReference")
    val sourceRoot = superIslandUpstreamsDir.get().resolve("KernelSU/manager/app/src/main/java/me/weishu/kernelsu")
    from(sourceRoot.resolve("ui/screen/colorpalette/ColorPaletteScreenMaterial.kt")) {
        into("me/weishu/kernelsu/ui/screen/colorpalette")
        filter { line ->
            line.replace("import me.weishu.kernelsu.R", "import io.github.superisland.R")
        }
    }
    from(sourceRoot.resolve("ui/screen/colorpalette")) {
        include(
            "ColorPaletteScreenMiuix.kt",
            "ColorPaletteUiState.kt",
        )
        into("me/weishu/kernelsu/ui/screen/colorpalette")
        filter { line ->
            line.replace("import me.weishu.kernelsu.R", "import io.github.superisland.R")
        }
    }
    from(sourceRoot.resolve("ui/component/material")) {
        include(
            "ExpressiveScaffold.kt",
            "ExpressiveToggleButton.kt",
            "ExpressiveSwitch.kt",
            "ExpressiveMenu.kt",
            "SegmentedList.kt",
            "TonalCard.kt",
            "TopBarBackButton.kt",
            "SearchBar.kt",
            "SnackBar.kt",
        )
        into("me/weishu/kernelsu/ui/component/material")
    }
    from(sourceRoot.resolve("ui/component/AppIconImage.kt")) {
        into("me/weishu/kernelsu/ui/component")
    }
    from(sourceRoot.resolve("ui/util/AppIconCache.kt")) {
        into("me/weishu/kernelsu/ui/util")
    }
    from(sourceRoot.resolve("ui/util/DeferredContent.kt")) {
        into("me/weishu/kernelsu/ui/util")
    }
    from(sourceRoot.resolve("ui/component/statustag")) {
        include("StatusTag.kt", "StatusTagMaterial.kt")
        into("me/weishu/kernelsu/ui/component/statustag")
    }
    from(sourceRoot.resolve("ui/component/miuix/ScaleDialog.kt")) {
        into("me/weishu/kernelsu/ui/component/miuix")
        filter { line ->
            line.replace("import me.weishu.kernelsu.R", "import io.github.superisland.R")
        }
    }
    from(sourceRoot.resolve("ui/screen/settings/SettingsUiState.kt")) {
        into("me/weishu/kernelsu/ui/screen/settings")
    }
    from(sourceRoot.resolve("ui/UiMode.kt")) {
        into("me/weishu/kernelsu/ui")
    }
    from(sourceRoot.resolve("ui/theme/ThemeExt.kt")) {
        into("me/weishu/kernelsu/ui/theme")
    }
    from(sourceRoot.resolve("ui/theme")) {
        include(
            "Colors.kt",
            "MaterialTheme.kt",
            "MiuixTheme.kt",
            "Type.kt",
        )
        into("me/weishu/kernelsu/ui/theme")
        filter { line ->
            when {
                line.contains("import me.weishu.kernelsu.ui.webui.MonetColorsProvider") -> ""
                line.contains("MonetColorsProvider.UpdateCss") -> ""
                else -> line
            }
        }
    }
    into(kernelSuMaterialGeneratedDir)

    doLast {
        val colorPaletteRoot =
            kernelSuMaterialGeneratedDir.get().asFile.resolve(
                "me/weishu/kernelsu/ui/screen/colorpalette",
            )
        val materialBadgeBlock =
            """
                SegmentedColumn(
                    modifier = Modifier.padding(top = 4.dp),
                    content = listOf(
                        {
                            SegmentedSwitchItem(
                                icon = Icons.Rounded.Pin,
                                title = stringResource(id = R.string.settings_navigation_badge),
                                summary = stringResource(id = R.string.settings_navigation_badge_summary),
                                checked = uiState.enableNavigationBadge,
                                onCheckedChange = actions.onSetEnableNavigationBadge
                            )
                        }
                    )
                )
            """.trimIndent().prependIndent("                ")
        val miuixBadgeBlock =
            """
                SwitchPreference(
                    title = stringResource(id = R.string.settings_navigation_badge),
                    summary = stringResource(id = R.string.settings_navigation_badge_summary),
                    startAction = {
                        Icon(
                            Icons.Rounded.Pin,
                            modifier = Modifier.padding(end = 6.dp),
                            contentDescription = stringResource(id = R.string.settings_navigation_badge),
                            tint = colorScheme.onBackground
                        )
                    },
                    checked = uiState.enableNavigationBadge,
                    onCheckedChange = {
                        actions.onSetEnableNavigationBadge(it)
                    }
                )
            """.trimIndent().prependIndent("                        ")
        listOf(
            colorPaletteRoot.resolve("ColorPaletteScreenMaterial.kt") to materialBadgeBlock,
            colorPaletteRoot.resolve("ColorPaletteScreenMiuix.kt") to miuixBadgeBlock,
        ).forEach { (source, block) ->
            val original = source.readText()
            check(block in original) { "KernelSU navigation badge block changed in ${source.name}" }
            source.writeText(original.replace(block, ""))
        }
    }
}

android.sourceSets.named("main") {
    java.srcDir(kernelSuMaterialGeneratedDir.get().asFile)
    kotlin.srcDir(kernelSuMaterialGeneratedDir.get().asFile)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    dependsOn(generateKernelSuMaterialThemeSource)
}

// Kept in lock-step with KernelSU Manager's Material theme layer. The expressive theme-mode
// controls are experimental APIs in Material 3 1.5.0-alpha23.
kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
        )
    }
}

abstract class VerifyBenchmarkXposedAbiTask @Inject constructor(
    private val execOperations: ExecOperations,
) : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val benchmarkApks: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val mappingFile: RegularFileProperty

    @get:Internal
    abstract val sdkDirectory: DirectoryProperty

    @TaskAction
    fun verify() {
        val apks = benchmarkApks.files.filter { it.isFile && it.extension == "apk" }
        if (apks.size != 1) {
            throw GradleException(
                "Expected exactly one final benchmark APK, found ${apks.size}: " +
                    apks.joinToString { it.absolutePath },
            )
        }
        val apk = apks.single()
        val mapping = mappingFile.get().asFile
        if (!mapping.isFile) {
            throw GradleException("Benchmark R8 mapping is missing: ${mapping.absolutePath}")
        }

        verifyXposedEntryResource(apk)
        val hookerDexClasses = verifyR8Mapping(mapping)
        findApkAnalyzer(sdkDirectory.get().asFile)?.let { apkAnalyzer ->
            verifyDexAbi(apkAnalyzer, apk, hookerDexClasses)
        } ?: logger.warn(
            "Android SDK apkanalyzer was not found; benchmark Xposed ABI remains verified " +
                "through the final APK entry resource and R8 mapping.",
        )
    }

    private fun verifyXposedEntryResource(apk: File) {
        val entries =
            ZipFile(apk).use { zip ->
                val entry =
                    zip.getEntry(XPOSED_ENTRY_RESOURCE)
                        ?: throw GradleException(
                            "Final benchmark APK is missing $XPOSED_ENTRY_RESOURCE: ${apk.absolutePath}",
                        )
                zip.getInputStream(entry).bufferedReader().useLines { lines ->
                    lines.map(String::trim)
                        .filter { it.isNotEmpty() && !it.startsWith("#") }
                        .toList()
                }
            }
        if (entries != listOf(XPOSED_ENTRYPOINT)) {
            throw GradleException(
                "$XPOSED_ENTRY_RESOURCE must contain only $XPOSED_ENTRYPOINT, found $entries",
            )
        }
    }

    private fun verifyR8Mapping(mapping: File): Set<String> {
        val classHeader = Regex("^(\\S+) -> (\\S+):$")
        val constructor =
            Regex("^\\s+(?:\\d+:\\d+:)?void <init>\\(\\)(?::\\d+(?::\\d+)?)? -> <init>$")
        val packageCallback =
            Regex(
                "^\\s+(?:\\d+:\\d+:)?void onPackageLoaded\\(" +
                    Regex.escape(PACKAGE_LOADED_PARAM) +
                    "\\)(?::\\d+(?::\\d+)?)? -> onPackageLoaded$",
            )
        val hookerCallback =
            Regex(
                "^\\s+(?:\\d+:\\d+:)?java\\.lang\\.Object " +
                    "(?:\\S+\\.)?intercept\\(" +
                    Regex.escape(HOOK_CHAIN) +
                    "\\)(?::\\d+(?::\\d+)?)? -> intercept$",
            )

        var currentOriginalClass: String? = null
        var currentDexClass: String? = null
        var entryClassFound = false
        var constructorFound = false
        var packageCallbackFound = false
        val hookerDexClasses = linkedSetOf<String>()

        mapping.useLines { lines ->
            lines.forEach { line ->
                classHeader.matchEntire(line)?.let { header ->
                    currentOriginalClass = header.groupValues[1]
                    currentDexClass = header.groupValues[2]
                    if (currentOriginalClass == XPOSED_ENTRYPOINT) {
                        entryClassFound = true
                        if (currentDexClass != XPOSED_ENTRYPOINT) {
                            throw GradleException(
                                "R8 renamed the Xposed entry class to $currentDexClass while " +
                                    "$XPOSED_ENTRY_RESOURCE still names $XPOSED_ENTRYPOINT",
                            )
                        }
                    }
                    return@forEach
                }
                if (currentOriginalClass == XPOSED_ENTRYPOINT) {
                    constructorFound = constructorFound || constructor.matches(line)
                    packageCallbackFound = packageCallbackFound || packageCallback.matches(line)
                }
                if (hookerCallback.matches(line)) {
                    currentDexClass?.let(hookerDexClasses::add)
                }
            }
        }

        val missing = buildList {
            if (!entryClassFound) add("entry class $XPOSED_ENTRYPOINT")
            if (!constructorFound) add("public zero-argument constructor")
            if (!packageCallbackFound) add("onPackageLoaded($PACKAGE_LOADED_PARAM)")
            if (hookerDexClasses.isEmpty()) add("Hooker.intercept($HOOK_CHAIN): Object")
        }
        if (missing.isNotEmpty()) {
            throw GradleException(
                "Benchmark R8 mapping is missing externally invoked Xposed ABI: " +
                    missing.joinToString(),
            )
        }
        return hookerDexClasses
    }

    private fun verifyDexAbi(
        apkAnalyzer: File,
        apk: File,
        mappedHookerClasses: Set<String>,
    ) {
        val packages =
            runApkAnalyzer(
                apkAnalyzer,
                "dex",
                "packages",
                "--defined-only",
                apk.absolutePath,
            )
        val entryClass = Regex("^C\\s+d(?:\\s+\\S+){3}\\s+${Regex.escape(XPOSED_ENTRYPOINT)}$")
        val entryConstructor =
            Regex("^M\\s+d(?:\\s+\\S+){3}\\s+${Regex.escape(XPOSED_ENTRYPOINT)} <init>\\(\\)$")
        val entryCallback =
            Regex(
                "^M\\s+d(?:\\s+\\S+){3}\\s+${Regex.escape(XPOSED_ENTRYPOINT)} " +
                    "void onPackageLoaded\\(${Regex.escape(PACKAGE_LOADED_PARAM)}\\)$",
            )
        val hookerMethod =
            Regex(
                "^M\\s+d(?:\\s+\\S+){3}\\s+(\\S+) java\\.lang\\.Object " +
                    "intercept\\(${Regex.escape(HOOK_CHAIN)}\\)$",
            )
        val packageLines = packages.lineSequence().toList()
        if (packageLines.none(entryClass::matches) ||
            packageLines.none(entryConstructor::matches) ||
            packageLines.none(entryCallback::matches)
        ) {
            throw GradleException(
                "Final benchmark DEX is missing the Xposed entry class, constructor, or " +
                    "onPackageLoaded callback even though R8 emitted a mapping.",
            )
        }

        val dexHookerCandidates =
            packageLines.mapNotNull { hookerMethod.matchEntire(it)?.groupValues?.get(1) }
                .filterTo(linkedSetOf()) { it in mappedHookerClasses }
        val validHooker =
            dexHookerCandidates.any { className ->
                val code =
                    runApkAnalyzer(
                        apkAnalyzer,
                        "dex",
                        "code",
                        "--class",
                        className,
                        apk.absolutePath,
                    )
                HOOKER_INTERFACE_DESCRIPTOR in code && HOOKER_METHOD_DESCRIPTOR.containsMatchIn(code)
            }
        if (!validHooker) {
            throw GradleException(
                "Final benchmark DEX has no mapped class that both implements " +
                    "XposedInterface.Hooker and defines intercept(Chain): Object.",
            )
        }
    }

    private fun findApkAnalyzer(sdk: File): File? {
        val executable = if (System.getProperty("os.name").startsWith("Windows", true)) "apkanalyzer.bat" else "apkanalyzer"
        val directCandidates =
            listOf(
                sdk.resolve("cmdline-tools/latest/bin/$executable"),
                sdk.resolve("tools/bin/$executable"),
            )
        directCandidates.firstOrNull(File::isFile)?.let { return it }
        return sdk.resolve("cmdline-tools").listFiles()
            ?.asSequence()
            ?.map { it.resolve("bin/$executable") }
            ?.filter(File::isFile)
            ?.sortedByDescending { it.parentFile.parentFile.name }
            ?.firstOrNull()
    }

    private fun runApkAnalyzer(apkAnalyzer: File, vararg arguments: String): String {
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val result =
            execOperations.exec {
                commandLine(apkAnalyzer.absolutePath, *arguments)
                standardOutput = stdout
                errorOutput = stderr
                isIgnoreExitValue = true
                environment("JAVA_HOME", System.getProperty("java.home"))
            }
        if (result.exitValue != 0) {
            throw GradleException(
                "apkanalyzer ${arguments.joinToString(" ")} failed with exit ${result.exitValue}: " +
                    stderr.toString(Charsets.UTF_8),
            )
        }
        return stdout.toString(Charsets.UTF_8)
    }

    private companion object {
        const val XPOSED_ENTRY_RESOURCE = "META-INF/xposed/java_init.list"
        const val XPOSED_ENTRYPOINT =
            "io.github.superisland.hook.systemui.SuperIslandXposedModule"
        const val PACKAGE_LOADED_PARAM =
            "io.github.libxposed.api.XposedModuleInterface\$PackageLoadedParam"
        const val HOOK_CHAIN = "io.github.libxposed.api.XposedInterface\$Chain"
        const val HOOKER_INTERFACE_DESCRIPTOR =
            ".implements Lio/github/libxposed/api/XposedInterface\$Hooker;"
        val HOOKER_METHOD_DESCRIPTOR =
            Regex(
                "(?m)^\\.method .* intercept\\(" +
                    "Lio/github/libxposed/api/XposedInterface\\\$Chain;" +
                    "\\)Ljava/lang/Object;$",
            )
    }
}

val verifyBenchmarkXposedAbi =
    tasks.register<VerifyBenchmarkXposedAbiTask>("verifyBenchmarkXposedAbi") {
        group = "verification"
        description = "Verifies the post-R8 benchmark APK retains the externally invoked Xposed ABI."
        dependsOn("assembleBenchmark")
        benchmarkApks.from(
            layout.buildDirectory.dir("outputs/apk/benchmark").map { output ->
                output.asFileTree.matching { include("*.apk") }
            },
        )
        mappingFile.set(layout.buildDirectory.file("outputs/mapping/benchmark/mapping.txt"))
        sdkDirectory.set(androidComponents.sdkComponents.sdkDirectory)
    }

tasks.named("check") {
    dependsOn(verifyBenchmarkXposedAbi)
}
