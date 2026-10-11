plugins {
    id("msuite.compose-library")
}

kotlin {
    android {
        namespace = "com.msuite.core.design"
    }
    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
        }
    }
}
