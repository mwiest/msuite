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

    buildTypes {
        release {
            // Inert until minification is turned on; then it keeps the crypto provider (see the file).
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

base {
    archivesName.set("todo")
}

dependencies {
    implementation(project(":apps:todo:shared"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.sqldelight.android.driver)
    implementation(libs.zxing.core)
}
