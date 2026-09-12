plugins {
    id("org.jetbrains.kotlin.jvm")
}

// Gradle does not generate the type-safe `libs` catalog accessor for precompiled script
// plugins (gradle/gradle#15383), so the catalog has to be looked up explicitly here.
val libs = extensions.getByType<org.gradle.api.artifacts.VersionCatalogsExtension>().named("libs")

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

// KGP defaults jvmTarget to the JDK running the daemon, which fails the JVM-target consistency
// check against the Java 11 setting above. (AGP's built-in Kotlin derives this from
// compileOptions automatically, so the Android convention plugins don't need it.)
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
    }
}

dependencies {
    "testImplementation"(libs.findLibrary("junit").get())
}
