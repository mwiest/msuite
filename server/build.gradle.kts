plugins {
    id("msuite.jvm-app")
}

application {
    mainClass.set("com.msuite.server.MainKt")
}

dependencies {
    implementation(project(":core:relay"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)

    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.ktor.server.test.host)
}
