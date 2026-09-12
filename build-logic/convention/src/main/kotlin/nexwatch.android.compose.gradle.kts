import com.android.build.api.dsl.CommonExtension

plugins {
    id("org.jetbrains.kotlin.plugin.compose")
}

// Gradle does not generate the type-safe `libs` catalog accessor for precompiled script
// plugins (gradle/gradle#15383), so the catalog has to be looked up explicitly here.
val libs = extensions.getByType<org.gradle.api.artifacts.VersionCatalogsExtension>().named("libs")

// Everything is deferred until an AGP plugin is present, so this plugin can be listed before or
// after nexwatch.android.library / nexwatch.android.application in a module's plugins block.
// Without the deferral, the dependencies block below fails with "Configuration with name
// 'implementation' not found" whenever this plugin is applied first.
pluginManager.withPlugin("com.android.base") {
    // `buildFeatures.compose` is assigned through the property rather than a nested
    // buildFeatures { } lambda: inside this file `compose` also names the generated
    // PluginDependencySpec accessor for org.jetbrains.kotlin.plugin.compose, which wins
    // name resolution in a lambda receiver and makes the assignment fail to compile.
    extensions.configure<CommonExtension> {
        buildFeatures.compose = true
    }

    dependencies {
        val bom = platform(libs.findLibrary("androidx-compose-bom").get())
        "implementation"(bom)
        "androidTestImplementation"(bom)
    }
}
