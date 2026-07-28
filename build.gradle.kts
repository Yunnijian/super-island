plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}

layout.buildDirectory.set(layout.projectDirectory.dir(".build/root"))

val verifyPinnedUpstreams = tasks.register<Exec>("verifyPinnedUpstreams") {
    group = "verification"
    description = "Checks the locked KernelSU and HyperIsland source checkouts without modifying them."
    workingDir(rootDir)
    commandLine("bash", file("scripts/bootstrap-upstreams.sh").absolutePath, "--check")
}

val verifyMiuixPolicy = tasks.register("verifyMiuixPolicy") {
    group = "verification"
    description = "Enforces renderer boundaries: Miuix pages and Material pages may not mix visual components."

    val designSystemSources = fileTree("modules/ui-design-system/src/main") {
        include("**/*.kt")
    }
    val appSources = fileTree("app/src/main") {
        include("**/*.kt")
    }
    inputs.files(designSystemSources, appSources)

    doLast {
        val violations = mutableListOf<String>()
        // Miuix screens may use GPL-upstream AndroidX vector assets (for example KernelSU's
        // status-card watermark), but may not draw Material or Material3 visual components.
        // KernelSU's own Miuix root theme uses Material3's non-visual dynamic palette resolvers
        // to pass the system seed into ThemeController.  Those two color-only functions are
        // permitted here; every actual Material component remains forbidden in Miuix sources.
        val materialImport =
            Regex(
                """^\s*import\s+androidx\.compose\.material3\.(?!dynamicDarkColorScheme$|dynamicLightColorScheme$)""",
                RegexOption.MULTILINE,
            )
        val directMiuixImport = Regex("""^\s*import\s+top\.yukonga\.miuix\.""", RegexOption.MULTILINE)
        val bareClickable = Regex("""Modifier(?:\s*\n\s*)?\.clickable\s*\(""")

        designSystemSources.files.forEach { source ->
            val text = source.readText()
            if (materialImport.containsMatchIn(text)) {
                violations += "${source.relativeTo(rootDir)} imports a Material visual component"
            }
            if (bareClickable.containsMatchIn(text)) {
                violations += "${source.relativeTo(rootDir)} creates a bare clickable control"
            }
        }

        appSources.files.forEach { source ->
            val text = source.readText()
            val relativePath = source.relativeTo(rootDir).invariantSeparatorsPath
            val isMaterialRenderer = relativePath.contains("/ui/material/")
            if (materialImport.containsMatchIn(text) && !isMaterialRenderer) {
                violations += "$relativePath imports a Material visual component outside ui/material"
            }
            if (directMiuixImport.containsMatchIn(text)) {
                violations += "$relativePath bypasses ui-design-system"
            }
            if (isMaterialRenderer && directMiuixImport.containsMatchIn(text)) {
                violations += "$relativePath mixes Miuix and Material renderers"
            }
            if (bareClickable.containsMatchIn(text)) {
                violations += "${source.relativeTo(rootDir)} creates a bare clickable control"
            }
        }

        check(violations.isEmpty()) {
            violations.joinToString(prefix = "Miuix policy violations:\n", separator = "\n")
        }
    }
}

// Generated KernelSU source is covered by the pinned-commit check and deterministic Sync task;
// this policy scans only product-owned adapters. Keep generation ordered before the policy so a
// missing or invalid upstream tree fails every normal build at the same boundary.
verifyMiuixPolicy.configure {
    dependsOn(":ui-design-system:generateKernelSuFloatingSource")
}

tasks.register("check") {
    group = "verification"
    dependsOn(verifyMiuixPolicy, ":app:verifyBenchmarkXposedAbi")
}

subprojects {
    tasks.matching { it.name == "preBuild" }.configureEach {
        dependsOn(rootProject.tasks.named("verifyMiuixPolicy"))
    }
}
