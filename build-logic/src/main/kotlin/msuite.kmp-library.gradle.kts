// A core/* library: Kotlin Multiplatform, targets android + jvm (jvm runs the tests).
// Modules set their namespace in `kotlin { android { namespace = ... } }`.

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("msuite.quality")
}

kotlin {
    android {
        msuiteAndroidDefaults()
    }
    jvm {
        compilerOptions { jvmTarget.set(MsuiteBuild.JVM_TARGET) }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
