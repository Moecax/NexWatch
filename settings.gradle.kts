pluginManagement {
    includeBuild("build-logic")
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

rootProject.name = "NexWatch"
include(":app")
include(":core:common")
include(":core:model")
include(":core:watch-api")
include(":core:watch-fake")
include(":core:watch-fitcloud")
include(":core:database")
include(":core:data")
include(":core:export")
include(":core:sync-api")
include(":core:service")
include(":core:designsystem")
