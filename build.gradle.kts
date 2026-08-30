// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.library) apply false
}

// The Taplink SDK is consumed as a -SNAPSHOT. Gradle caches changing modules for 24h by
// default, so a freshly published snapshot is silently ignored and the app keeps running an
// old SDK build while the version string looks correct. Disable that cache so every build
// picks up the latest snapshot.
subprojects {
    configurations.all {
        resolutionStrategy {
            cacheChangingModulesFor(0, "seconds")
            cacheDynamicVersionsFor(0, "seconds")
        }
    }
}