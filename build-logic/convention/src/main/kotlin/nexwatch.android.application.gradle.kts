import com.android.build.api.dsl.ApplicationExtension

// AGP 9 compiles Kotlin itself, so `org.jetbrains.kotlin.android` is deliberately not applied
// here — the same shape `:app` already builds with.
plugins {
    id("com.android.application")
}

// Gradle does not generate the type-safe `libs` catalog accessor for precompiled script
// plugins (gradle/gradle#15383), so the catalog has to be looked up explicitly here.
val libs = extensions.getByType<org.gradle.api.artifacts.VersionCatalogsExtension>().named("libs")

extensions.configure<ApplicationExtension> {
    compileSdk = libs.findVersion("androidCompileSdk").get().requiredVersion.toInt()

    defaultConfig {
        minSdk = libs.findVersion("androidMinSdk").get().requiredVersion.toInt()
        targetSdk = libs.findVersion("androidTargetSdk").get().requiredVersion.toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    "testImplementation"(libs.findLibrary("junit").get())
}
