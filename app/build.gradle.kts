plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.boguan.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.boguan.app"
        minSdk = 29
        targetSdk = 36
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

    implementation(platform("androidx.compose:compose-bom:2026.05.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    // 组件统一用 Miuix（ADR-0014），不再依赖 material3
    // Miuix：HyperOS 风格组件库（ADR-0014）。0.8.8 是最后一个 minCompileSdk=36 的版本，
    // 0.9.x 起要求 compileSdk 37（需 AGP 9.x），当前工具链暂不升级。
    implementation("top.yukonga.miuix.kmp:miuix-android:0.8.8")
    // Markdown 渲染：0.41.0 是最后一个 minCompileSdk=36 的版本（0.42+ 需 compileSdk 37）
    implementation("com.mikepenz:multiplatform-markdown-renderer:0.41.0")
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.13.0")
}
