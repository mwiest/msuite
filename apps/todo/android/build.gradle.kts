plugins {
    id("msuite.android-app")
}

android {
    namespace = "com.msuite.todo"

    defaultConfig {
        applicationId = "com.msuite.todo"
        versionCode = 1
        versionName = "0.1.0"
    }
}

base {
    archivesName.set("todo")
}

dependencies {
    implementation(project(":apps:todo:shared"))
    implementation(libs.androidx.activity.compose)
}
