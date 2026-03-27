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
        //release
        maven("https://repo1.maven.org/maven2/")
        //snapshots
        maven("https://central.sonatype.com/repository/maven-snapshots/")
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
        //release
        maven("https://repo1.maven.org/maven2/")
        //snapshots
        maven("https://central.sonatype.com/repository/maven-snapshots/")
    }
}

rootProject.name = "TaplinkDemo"
include(":app")
include(":app-compose")
include(":lib_service")
