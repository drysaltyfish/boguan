plugins {
    id("com.android.library")
}

android {
    namespace = "com.boguan.asr"
    compileSdk = 37

    defaultConfig {
        minSdk = 31
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin { jvmToolchain(17) }
}

dependencies {
    implementation(project(":core:model"))
    api(files("libs/sherpa-onnx-2.0.1.aar"))
}