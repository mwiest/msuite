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
        }
        jvmTest.dependencies {
            implementation(libs.kotest.property)
            implementation(libs.kotest.assertions.core)
            implementation(libs.kotest.runner.junit5)
        }
    }
}
