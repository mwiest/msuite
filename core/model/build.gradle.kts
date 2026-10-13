plugins {
    id("msuite.kmp-library")
    id("msuite.serialization")
}

kotlin {
    android {
        namespace = "com.msuite.core.model"
    }
    sourceSets {
        commonMain.dependencies {
            // Batch/snapshot encoding via kotlinx.serialization + ProtoBuf (decision 0016)
            implementation(libs.kotlinx.serialization.core)
            implementation(libs.kotlinx.serialization.protobuf)
            implementation(libs.kotlinx.datetime)
        }
        jvmTest.dependencies {
            implementation(libs.kotest.property)
            implementation(libs.kotest.assertions.core)
            implementation(libs.kotest.runner.junit5)
        }
    }
}
