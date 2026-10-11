plugins {
    id("msuite.kmp-library")
    id("msuite.serialization")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Batch/snapshot encoding via kotlinx.serialization + ProtoBuf (see msuite.md -> Batch encoding)
            implementation(libs.kotlinx.serialization.core)
            implementation(libs.kotlinx.serialization.protobuf)
            implementation(libs.kotlinx.datetime)
        }
    }
}

android {
    namespace = "com.msuite.core.model"
}
