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
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
        }
        // Merge/HLC simulator harness (OQ-7) runs on the jvm target.
        jvmTest.dependencies {
            implementation(libs.kotest.property)
            implementation(libs.kotest.assertions.core)
            implementation(libs.kotest.runner.junit5)
        }
    }
}

android {
    namespace = "com.msuite.core.sync"
    compileSdk = 36 // TODO: build-logic convention (AGENTS.md)
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
