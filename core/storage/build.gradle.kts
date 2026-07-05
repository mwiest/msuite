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
            // TODO: SQLDelight (libs.sqldelight.runtime + plugin) — op-log + materialized
            //       per-field state store with opaque unknown-field retention (OQ-2, OQ-6)
        }
    }
}

android {
    namespace = "com.msuite.core.storage"
    compileSdk = 36 // TODO: build-logic convention (AGENTS.md)
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
