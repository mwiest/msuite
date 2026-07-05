import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

// Build on JDK 21 (toolchain), emit 17 bytecode — consistent with the app/core.
java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

application {
    mainClass.set("com.msuite.server.MainKt")
}

dependencies {
    // TODO: Ktor server — CIO engine + programmatic embeddedServer (no HOCON module loading),
    //       filesystem blob storage, reuse the OQ-3 name-grammar from core.
    //       Kept reflection-averse so a GraalVM-native build stays cheap (see msuite.md -> relay).
    //   implementation(libs.ktor.server.core)
    //   implementation(libs.ktor.server.cio)
}
