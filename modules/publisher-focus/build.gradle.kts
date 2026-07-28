plugins {
    alias(libs.plugins.android.library)
}

@Suppress("UnstableApiUsage")
android {
    namespace = "io.github.superisland.publisher.focus"
    buildToolsVersion = "37.0.0"
    compileSdk {
        version = release(37) {
            minorApiLevel = 0
        }
    }

    defaultConfig {
        minSdk = 36
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(project(":core-model"))
    implementation(libs.androidx.core)

    testImplementation(libs.junit4)
}
