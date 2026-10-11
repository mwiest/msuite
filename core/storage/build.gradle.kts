plugins {
    id("msuite.kmp-library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:model"))
            // TODO: SQLDelight (libs.sqldelight.runtime + plugin) — op-log + materialized
            //       per-field state store with opaque unknown-field retention (OQ-2, OQ-6)
        }
    }
}

android {
    namespace = "com.msuite.core.storage"
}
