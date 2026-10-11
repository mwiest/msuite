plugins {
    id("msuite.kmp-library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:model"))
            // TODO: SQLDelight (libs.sqldelight.runtime + plugin) — op-log + materialized
            //       per-field state store with opaque unknown-field retention (decision 0002, decision 0006)
        }
    }
}

android {
    namespace = "com.msuite.core.storage"
}
