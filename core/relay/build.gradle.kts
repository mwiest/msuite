plugins {
    id("msuite.kmp-library")
}

kotlin {
    android {
        namespace = "com.msuite.core.relay"
    }
    sourceSets {
        commonMain.dependencies {
            api(project(":core:sync"))
            api(libs.ktor.client.core)
        }
        jvmTest.dependencies {
            implementation(libs.kotest.property)
            implementation(libs.kotest.assertions.core)
            implementation(libs.kotest.runner.junit5)
        }
    }
}
