plugins {
    id("msuite.compose-library")
}

kotlin {
    android {
        namespace = "com.msuite.todo.shared"
        // Packages composeResources into the APK; off by default for KMP libraries.
        androidResources.enable = true
    }
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:design"))
            implementation(project(":apps:todo:domain"))

            api(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.components.resources)

            // TODO: Navigation Compose (libs.navigation.compose), multiplatform-settings, Kermit
        }
    }
}

compose.resources {
    packageOfResClass = "com.msuite.todo.resources"
}
