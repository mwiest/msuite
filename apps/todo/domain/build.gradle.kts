plugins {
    id("msuite.kmp-library")
}

kotlin {
    android {
        namespace = "com.msuite.todo.domain"
    }
    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            api(project(":core:sync"))
        }
        jvmTest.dependencies {
            implementation(project(":core:sync-testing"))
            implementation(libs.sqldelight.sqlite.driver)
            implementation(libs.kotest.property)
            implementation(libs.kotest.assertions.core)
            implementation(libs.kotest.runner.junit5)
        }
    }
}
