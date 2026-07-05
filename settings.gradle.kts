pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "msuite"

// core/* — shared, small modules by boundary (apps depend on these, never on each other)
include(":core:model")
include(":core:storage")
include(":core:sync")
include(":core:crypto")
include(":core:design")

// app modules (com.msuite.<app>)
include(":apps:todo")

// dumb relay (Ktor/Kotlin)
include(":server")
