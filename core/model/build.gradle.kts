import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    sourceSets {
        commonMain.dependencies {
            // Batch/snapshot encoding via kotlinx.serialization + ProtoBuf (see msuite.md -> Batch encoding)
            implementation(libs.kotlinx.serialization.core)
            implementation(libs.kotlinx.serialization.protobuf)
            implementation(libs.kotlinx.datetime)
        }
    }
}

android {
    namespace = "com.msuite.core.model"
    compileSdk = 36 // TODO: build-logic convention (AGENTS.md)
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
