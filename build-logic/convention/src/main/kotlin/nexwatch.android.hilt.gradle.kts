// Hilt configuration for Android modules. Apply this plugin to any Android module
// that uses Hilt dependency injection.
plugins {
    id("com.google.dagger.hilt.android")
}

// Gradle does not generate the type-safe `libs` catalog accessor for precompiled script
// plugins (gradle/gradle#15383), so the catalog has to be looked up explicitly here.
val libs = extensions.getByType<org.gradle.api.artifacts.VersionCatalogsExtension>().named("libs")

// Defer until an AGP plugin is present, otherwise the dependencies block fails with
// "Configuration with name 'implementation' not found".
pluginManager.withPlugin("com.android.base") {
    dependencies {
        // Hilt is configured here; individual modules declare hilt dependencies as needed.
    }
}
