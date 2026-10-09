pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "debugkit-android"

// Enables `projects.core` typed accessors instead of stringly-typed paths.
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// Published artifacts. Each one becomes `<GROUP>:<module>:<VERSION>`.
include(":core")
include(":network")
include(":logs")
include(":noop")

// Demo app; never published.
include(":sample")
