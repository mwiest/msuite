// Module configuration lives in the build-logic convention plugins (msuite.*).
// The root project only lints the root scripts and build-logic itself.
plugins {
    id("msuite.quality")
}

spotless {
    kotlin {
        target("build-logic/src/**/*.kt")
    }
    kotlinGradle {
        target("*.gradle.kts", "build-logic/*.gradle.kts", "build-logic/src/**/*.gradle.kts")
    }
}

detekt {
    source.setFrom("build-logic/src")
}
