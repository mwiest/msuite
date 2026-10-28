plugins {
    id("msuite.kmp-library")
}

kotlin {
    android {
        namespace = "com.msuite.core.sync"
    }
    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            api(project(":core:storage"))
            api(project(":core:crypto"))
            api(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kermit)
        }
        // Merge/HLC simulator harness (decision 0007) runs on the jvm target.
        jvmTest.dependencies {
            implementation(project(":core:sync-testing"))
            implementation(libs.sqldelight.sqlite.driver)
            implementation(libs.kotest.property)
            implementation(libs.kotest.assertions.core)
            implementation(libs.kotest.runner.junit5)
        }
    }
}
