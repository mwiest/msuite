plugins {
    id("msuite.kmp-library")
    id("msuite.sqldelight")
}

kotlin {
    android {
        namespace = "com.msuite.core.storage"
    }
    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            api(libs.sqldelight.runtime)
        }
        jvmTest.dependencies {
            implementation(libs.sqldelight.sqlite.driver)
            implementation(libs.kotest.property)
            implementation(libs.kotest.assertions.core)
            implementation(libs.kotest.runner.junit5)
        }
    }
}

sqldelight {
    databases {
        create("MsuiteDatabase") {
            packageName.set("com.msuite.core.storage.db")
        }
    }
}
