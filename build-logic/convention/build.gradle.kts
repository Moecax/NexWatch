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
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    // Kotlin 2.x ships the Compose compiler as a separate plugin artifact from kotlin-gradle-plugin.
    compileOnly("org.jetbrains.kotlin:compose-compiler-gradle-plugin:${libs.versions.kotlin.get()}")
}
