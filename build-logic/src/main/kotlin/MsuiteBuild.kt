import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.JavaVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/** Build settings shared by every module. */
object MsuiteBuild {
    const val COMPILE_SDK = 37
    const val TARGET_SDK = 37
    const val MIN_SDK = 26

    /** JDK that builds the code; the bytecode targets [JAVA_VERSION]. */
    const val JDK_TOOLCHAIN = 21
    val JAVA_VERSION = JavaVersion.VERSION_17
    val JVM_TARGET = JvmTarget.JVM_17
}

/** Android target of a KMP module (AGP's `com.android.kotlin.multiplatform.library`). */
internal fun KotlinMultiplatformAndroidLibraryTarget.msuiteAndroidDefaults() {
    compileSdk = MsuiteBuild.COMPILE_SDK
    minSdk = MsuiteBuild.MIN_SDK
    compilerOptions { jvmTarget.set(MsuiteBuild.JVM_TARGET) }
}

internal fun ApplicationExtension.msuiteAndroidDefaults() {
    compileSdk = MsuiteBuild.COMPILE_SDK
    defaultConfig.minSdk = MsuiteBuild.MIN_SDK
    defaultConfig.targetSdk = MsuiteBuild.TARGET_SDK
    compileOptions {
        sourceCompatibility = MsuiteBuild.JAVA_VERSION
        targetCompatibility = MsuiteBuild.JAVA_VERSION
    }
}
