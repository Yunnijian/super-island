import org.gradle.api.initialization.resolve.RepositoriesMode

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "super-island"

include(":app")

mapOf(
    ":core-model" to "modules/core-model",
    ":hook-systemui" to "modules/hook-systemui",
    ":hyperlyric-port" to "modules/hyperlyric-port",
    ":publisher-focus" to "modules/publisher-focus",
    ":source-lyric" to "modules/source-lyric",
    ":source-notification" to "modules/source-notification",
    ":source-root" to "modules/source-root",
    ":source-screenrecord" to "modules/source-screenrecord",
    ":test-source" to "samples/test-source",
    ":ui-design-system" to "modules/ui-design-system",
).forEach { (projectPath, projectDirectory) ->
    include(projectPath)
    project(projectPath).projectDir = file(projectDirectory)
}
