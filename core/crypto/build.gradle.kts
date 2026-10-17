plugins {
    id("msuite.kmp-library")
}

kotlin {
    android {
        namespace = "com.msuite.core.crypto"
    }
    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            // SHA-256, HKDF-SHA256, AES-256-GCM and the RNG; we only compose them (decision 0010).
            implementation(libs.cryptography.core)
            implementation(libs.cryptography.random)
        }
        // The JDK provider (JCA, Conscrypt on Android) backs android and jvm; web gets its own (0012).
        androidMain.dependencies {
            implementation(libs.cryptography.provider.jdk)
        }
        jvmMain.dependencies {
            implementation(libs.cryptography.provider.jdk)
        }
        jvmTest.dependencies {
            implementation(libs.kotest.property)
            implementation(libs.kotest.assertions.core)
            implementation(libs.kotest.runner.junit5)
        }
    }
}
