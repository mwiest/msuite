plugins {
    id("msuite.compose-library")
}

kotlin {
    android {
        namespace = "com.msuite.todo.shared"
    }
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:design"))
            implementation(project(":core:model"))

            api(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)

            // TODO: Navigation Compose (libs.navigation.compose), multiplatform-settings, Kermit
        }
    }
}
