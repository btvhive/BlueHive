// settings.gradle.kts (Root Project)

pluginManagement {
    repositories {
        google()
        maven { url = uri("https://jitpack.io") }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS) // ✅ Better practice
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "BlueHive"
include(":app")
include(":baselineprofile")