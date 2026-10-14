// A Kotlin/JVM library: built on the JDK toolchain, emits Java 17 bytecode.

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("msuite.quality")
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(MsuiteBuild.JDK_TOOLCHAIN)) }
    sourceCompatibility = MsuiteBuild.JAVA_VERSION
    targetCompatibility = MsuiteBuild.JAVA_VERSION
}

kotlin {
    compilerOptions { jvmTarget.set(MsuiteBuild.JVM_TARGET) }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
