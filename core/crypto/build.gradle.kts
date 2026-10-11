plugins {
    id("msuite.kmp-library")
}

kotlin {
    android {
        namespace = "com.msuite.core.crypto"
    }
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:model"))
            // TODO: cryptography-kotlin (libs.cryptography.core) — HKDF-SHA256 -> AES-256-GCM
            //       E2E of batches, single-QR pairing, BIP39 recovery (decision 0008, decision 0010)
        }
    }
}
