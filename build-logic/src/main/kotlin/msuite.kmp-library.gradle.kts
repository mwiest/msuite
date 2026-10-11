// A core/* library: Kotlin Multiplatform, targets android + jvm (jvm runs the tests).

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(MsuiteBuild.JVM_TARGET) }
    }
    jvm {
        compilerOptions { jvmTarget.set(MsuiteBuild.JVM_TARGET) }
    }
}

android {
    msuiteAndroidDefaults()
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
