// An Android application shell (apps/<app>/android). Kotlin is built into AGP 9.

plugins {
    id("com.android.application")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("msuite.quality")
}

android {
    msuiteAndroidDefaults()
    buildFeatures { compose = true }
}

kotlin {
    compilerOptions { jvmTarget.set(MsuiteBuild.JVM_TARGET) }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
