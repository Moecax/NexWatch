plugins {
    `kotlin-dsl`
}

group = "com.nexwatch.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// Keep Kotlin's target in step with the Java one above; otherwise KGP warns about inconsistent
// JVM targets because it defaults to the (much newer) JDK running the Gradle daemon.
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    // implementation, not compileOnly: precompiled script plugins need the plugin classes on
    // their own runtime classpath to apply id(...) reliably, pinned to the catalog's version —
    // compileOnly doesn't reach the classpath of a project that applies the plugin via id(...).
    implementation(libs.android.gradlePlugin)
    implementation(libs.kotlin.gradlePlugin)
    // Kotlin 2.x ships the Compose compiler as a separate plugin artifact from kotlin-gradle-plugin.
    implementation("org.jetbrains.kotlin:compose-compiler-gradle-plugin:${libs.versions.kotlin.get()}")
    implementation(libs.hilt.android.gradlePlugin)
}
