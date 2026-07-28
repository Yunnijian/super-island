plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
}

@Suppress("UnstableApiUsage")
android {
    namespace = "io.github.superisland.design"
    buildToolsVersion = "37.0.0"
    compileSdk {
        version = release(37) {
            minorApiLevel = 0
        }
    }

    defaultConfig {
        minSdk = 36
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(project(":core-model"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material.icons.extended)
    // Non-visual dependencies used by the verbatim KernelSU Miuix theme adapter: dynamic
    // palette resolution and status/navigation bar appearance. Miuix remains the only UI
    // component library exposed by this module.
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material.kolor)
    api(libs.miuix.ui)
    implementation(libs.miuix.blur)
    api(libs.miuix.preference)
    implementation(libs.miuix.icons)
    implementation(libs.compose.foundation)
    implementation(libs.androidx.navigationevent.compose)
}

// KernelSU's floating navigation and liquid-glass implementation are generated from the same
// pinned upstream tree as the theme pages. The sole import rewrite supplies the product shell's
// dark-mode state; animation, blur, gesture, and shader source bodies remain unchanged.
val kernelSuFloatingGeneratedDir = layout.buildDirectory.dir("generated/source/kernelsuFloating")
val kernelSuReferenceCommit = "b6e50f9a4f5fa7a14b68e7945d172ddbeae36415"
val superIslandUpstreamsDir =
    providers.gradleProperty("superIslandUpstreamsDir")
        .orElse(providers.environmentVariable("SUPER_ISLAND_UPSTREAMS_DIR"))
        .orElse("${System.getProperty("user.home")}/.cache/super-island/upstreams")
        .map(::file)
val kernelSuSourceRoot = superIslandUpstreamsDir.get().resolve("KernelSU")

val verifyKernelSuReference = tasks.register("verifyKernelSuReference") {
    group = "verification"
    description = "Checks that the vendored KernelSU Manager tree is pinned to the reviewed commit."
    dependsOn(rootProject.tasks.named("verifyPinnedUpstreams"))
    inputs.dir(kernelSuSourceRoot)
    doLast {
        val output = providers.exec {
            commandLine("git", "-C", kernelSuSourceRoot.absolutePath, "rev-parse", "HEAD")
        }.standardOutput.asText.get().trim()
        check(output == kernelSuReferenceCommit) {
            "KernelSU reference mismatch: expected $kernelSuReferenceCommit, got $output"
        }
    }
}

val generateKernelSuFloatingSource = tasks.register<Sync>("generateKernelSuFloatingSource") {
    dependsOn(verifyKernelSuReference)
    val sourceRoot = kernelSuSourceRoot.resolve("manager/app/src/main/java/me/weishu/kernelsu")
    from(sourceRoot.resolve("ui/util/BlurExt.kt")) {
        into("me/weishu/kernelsu/ui/util")
    }
    from(sourceRoot.resolve("ui/component/FloatingBottomBar.kt")) {
        into("me/weishu/kernelsu/ui/component")
        filter { line ->
            line.replace(
                "import me.weishu.kernelsu.ui.theme.isInDarkTheme",
                "import io.github.superisland.design.isInDarkTheme",
            )
        }
    }
    from(sourceRoot.resolve("ui/component/liquid")) {
        include("CombinedBackdrop.kt", "InnerShadow.kt", "Lens.kt", "Vibrancy.kt")
        into("me/weishu/kernelsu/ui/component/liquid")
    }
    from(sourceRoot.resolve("ui/component/miuix/animation")) {
        include("DampedDragAnimation.kt", "InteractiveHighlight.kt")
        into("me/weishu/kernelsu/ui/component/miuix/animation")
    }
    from(sourceRoot.resolve("ui/component/miuix/modifier/DragGestureInspector.kt")) {
        into("me/weishu/kernelsu/ui/component/miuix/modifier")
    }
    from(sourceRoot.resolve("ui/component/SearchStatus.kt")) {
        into("me/weishu/kernelsu/ui/component")
    }
    from(sourceRoot.resolve("ui/component/MenuPositionProvider.kt")) {
        into("me/weishu/kernelsu/ui/component")
    }
    from(sourceRoot.resolve("ui/component/ScrollToTop.kt")) {
        into("me/weishu/kernelsu/ui/component")
    }
    from(sourceRoot.resolve("ui/component/miuix/SuperSearchBar.kt")) {
        into("me/weishu/kernelsu/ui/component/miuix")
    }
    from(sourceRoot.resolve("ui/component/statustag/StatusTagMiuix.kt")) {
        into("me/weishu/kernelsu/ui/component/statustag")
    }
    // Keep generated output under build/ so stale upstream files cannot remain in the source tree.
    into(kernelSuFloatingGeneratedDir)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    source(kernelSuFloatingGeneratedDir)
    dependsOn(generateKernelSuFloatingSource)
}
