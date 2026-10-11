// A Compose Multiplatform library. Source stays in commonMain, but only android is built for now.
// Modules set their namespace in `kotlin { android { namespace = ... } }`.

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("msuite.quality")
}

kotlin {
    android {
        msuiteAndroidDefaults()
    }
}
