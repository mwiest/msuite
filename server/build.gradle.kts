plugins {
    id("msuite.jvm-app")
}

application {
    mainClass.set("com.msuite.server.MainKt")
}

dependencies {
    // TODO: Ktor server — CIO engine + programmatic embeddedServer (no HOCON module loading),
    //       filesystem blob storage, reuse the decision 0003 name-grammar from core.
    //       Kept reflection-averse so a GraalVM-native build stays cheap (decision 0017).
    //   implementation(libs.ktor.server.core)
    //   implementation(libs.ktor.server.cio)
}
