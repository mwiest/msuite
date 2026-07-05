// Root build.
//
// Plugins are declared here with `apply false` so each is loaded once on the root
// classpath and reused by subprojects (versions come from gradle/libs.versions.toml).
//
// Shared module configuration (compileSdk/minSdk/jvmTarget, quality tooling) is still
// repeated per-module with `// TODO: build-logic` markers. Extracting it into
// `build-logic` convention plugins — plus wiring Spotless(ktlint) + detekt — is a
// tracked build task (see AGENTS.md -> Build / test / run).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
}
