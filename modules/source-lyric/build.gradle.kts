plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

@Suppress("UnstableApiUsage")
android {
    namespace = "io.github.superisland.source.lyric"
    buildToolsVersion = "37.0.0"
    compileSdk {
        version = release(37) {
            minorApiLevel = 0
        }
    }

    defaultConfig { minSdk = 36 }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(project(":core-model"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.daimajia.animations) { artifact { type = "aar" } }
    implementation(libs.daimajia.easing) { artifact { type = "aar" } }
    implementation(libs.androidx.interpolator)
    implementation(libs.superlyric.api)
}
