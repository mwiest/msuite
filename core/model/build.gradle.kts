plugins {
    id("msuite.kmp-library")
    id("msuite.serialization")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Batch/snapshot encoding via kotlinx.serialization + ProtoBuf (decision 0016)
            implementation(libs.kotlinx.serialization.core)
            implementation(libs.kotlinx.serialization.protobuf)
            implementation(libs.kotlinx.datetime)
        }
    }
}

android {
    namespace = "com.msuite.core.model"
}
