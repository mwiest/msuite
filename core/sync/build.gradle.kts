plugins {
    id("msuite.kmp-library")
}

kotlin {
    android {
        namespace = "com.msuite.core.sync"
    }
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:model"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
        }
        // Merge/HLC simulator harness (decision 0007) runs on the jvm target.
        jvmTest.dependencies {
            implementation(libs.kotest.property)
            implementation(libs.kotest.assertions.core)
            implementation(libs.kotest.runner.junit5)
        }
    }
}
