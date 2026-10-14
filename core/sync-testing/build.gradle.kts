// The multi-device simulator harness (decisions 0007, 0057). Only test source sets depend on it.

plugins {
    id("msuite.jvm-library")
}

dependencies {
    api(project(":core:sync"))
    api(libs.sqldelight.sqlite.driver)
    api(libs.kotest.property)
    api(libs.kotest.assertions.core)
}
