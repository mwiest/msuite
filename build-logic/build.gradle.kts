plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.android.gradlePlugin)
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.kotlin.serialization.gradlePlugin)
    implementation(libs.compose.compiler.gradlePlugin)
    implementation(libs.compose.gradlePlugin)
    implementation(libs.spotless.gradlePlugin)
    implementation(libs.detekt.gradlePlugin)
    implementation(libs.sqldelight.gradlePlugin)
}
