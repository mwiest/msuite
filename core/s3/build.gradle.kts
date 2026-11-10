plugins {
    id("msuite.kmp-library")
}

kotlin {
    android {
        namespace = "com.msuite.core.s3"
    }
    sourceSets {
        commonMain.dependencies {
            api(project(":core:sync"))
            api(libs.ktor.client.core)
            implementation(project(":core:crypto"))
            implementation(libs.kotlinx.datetime)
        }
        jvmTest.dependencies {
            implementation(project(":core:sync-testing"))
            implementation(libs.ktor.client.mock)
            implementation(libs.kotest.assertions.core)
            implementation(libs.kotest.runner.junit5)
        }
    }
}
