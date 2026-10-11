// An apps/* module: Android application with Compose.

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    msuiteAndroidDefaults()
    defaultConfig.targetSdk = MsuiteBuild.TARGET_SDK
    buildFeatures { compose = true }
}

kotlin {
    compilerOptions { jvmTarget.set(MsuiteBuild.JVM_TARGET) }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
