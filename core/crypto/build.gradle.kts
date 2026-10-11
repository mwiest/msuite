plugins {
    id("msuite.kmp-library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:model"))
            // TODO: cryptography-kotlin (libs.cryptography.core) — HKDF-SHA256 -> AES-256-GCM
            //       E2E of batches, single-QR pairing, BIP39 recovery (decision 0008, decision 0010)
        }
    }
}

android {
    namespace = "com.msuite.core.crypto"
}
