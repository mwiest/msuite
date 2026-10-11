plugins {
    id("msuite.android-app")
}

android {
    namespace = "com.msuite.todo"

    defaultConfig {
        applicationId = "com.msuite.todo"
        versionCode = 1
        versionName = "0.1.0"
    }
}

dependencies {
    implementation(project(":core:design"))
    implementation(project(":core:model"))

    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.androidx.activity.compose)

    // TODO: Navigation Compose (libs.androidx.navigation.compose), multiplatform-settings, Kermit
}
