plugins {
    alias(libs.plugins.android.application)
}

@Suppress("UnstableApiUsage")
android {
    namespace = "io.github.superisland.testsource"
    buildToolsVersion = "37.0.0"
    compileSdk {
        version = release(37) {
            minorApiLevel = 0
        }
    }

    defaultConfig {
        applicationId = "io.github.superisland.testsource"
        minSdk = 36
        targetSdk = 36
        versionCode = 1
        versionName = "0.3.0-m3-test"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
