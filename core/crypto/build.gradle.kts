import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
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
            implementation(project(":core:model"))
            // TODO: cryptography-kotlin (libs.cryptography.core) — HKDF-SHA256 -> AES-256-GCM
            //       E2E of batches, single-QR pairing, BIP39 recovery (OQ-8, OQ-10)
        }
    }
}

android {
    namespace = "com.msuite.core.crypto"
    compileSdk = 36 // TODO: build-logic convention (AGENTS.md)
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
