plugins {
    alias(libs.plugins.android.library)
}

@Suppress("UnstableApiUsage")
android {
    namespace = "io.github.superisland.source.screenrecord"
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
    implementation(project(":publisher-focus"))
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit4)
}
