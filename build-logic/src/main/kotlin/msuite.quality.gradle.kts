// Formatting (Spotless + ktlint, rules in .editorconfig) and static analysis (detekt).

plugins {
    id("com.diffplug.spotless")
    id("io.gitlab.arturbosch.detekt")
}

val editorConfig = rootProject.file(".editorconfig")

spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint().setEditorConfigPath(editorConfig)
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint().setEditorConfigPath(editorConfig)
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    // The default source set list misses the KMP ones (commonMain, jvmTest, ...).
    source.setFrom("src")
}
