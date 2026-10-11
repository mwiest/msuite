// A Compose Multiplatform library. Source stays in commonMain, but only android is built for now.

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(MsuiteBuild.JVM_TARGET) }
    }
}

android {
    msuiteAndroidDefaults()
}
