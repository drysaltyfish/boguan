plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.boguan.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.boguan.app"
        // 全面适配：最低 Android 12（API 31），见 ADR-0015
        minSdk = 31
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0-alpha01"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin { jvmToolchain(17) }

    buildFeatures { compose = true }

    packaging { jniLibs { useLegacyPackaging = true } }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:audio"))
    implementation(project(":core:asr"))
    implementation(project(":core:store"))
    implementation(project(":core:summarize"))
    implementation(project(":core:report"))

    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.13.0")

    // 组件统一用 Miuix（ADR-0014）。0.9.x 起要求 compileSdk 37，见 ADR-0015。
    implementation("top.yukonga.miuix.kmp:miuix-ui-android:0.9.4")
    // 报告 Markdown 渲染（配色/字体取自 Miuix 主题）
    implementation("com.mikepenz:multiplatform-markdown-renderer:0.46.0")
}