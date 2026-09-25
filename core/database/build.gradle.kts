import org.gradle.api.attributes.java.TargetJvmEnvironment

plugins {
    id("nexwatch.android.library")
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.nexwatch.core.database"

    testFixtures {
        enable = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.sqlite.bundled)

    // inMemoryTestDatabase() lives in src/testFixtures so other modules' tests can reuse it
    // (CLAUDE.md I4 trigger installation must stay in exactly one place). These are `api`, not
    // `implementation`, because consumers call `.close()` on the returned NexWatchDatabase
    // (a RoomDatabase) and need Room/sqlite types on their own compile classpath.
    testFixturesApi(libs.androidx.room.runtime)
    testFixturesApi(libs.androidx.sqlite.bundled)
    testFixturesImplementation(libs.kotlinx.coroutines.core)
}

// Room and sqlite-bundled publish separate Kotlin/Multiplatform variants for "android" and
// "standard-jvm" environments (see their Gradle module metadata). The android variant's
// Room.inMemoryDatabaseBuilder() requires a Context and its BundledSQLiteDriver native loader
// expects an Android runtime to resolve the .so — neither works on a plain desktop JVM test run.
// These unit tests run as ordinary JVM tests (no emulator/Robolectric), so their classpaths must
// resolve the standard-jvm variant instead, which is what gives access to the context-free
// Room.inMemoryDatabaseBuilder<T>() entry point Task 11 relies on.
afterEvaluate {
    configurations.matching {
        it.name == "debugUnitTestCompileClasspath" ||
            it.name == "debugUnitTestRuntimeClasspath" ||
            it.name == "debugTestFixturesCompileClasspath" ||
            it.name == "debugTestFixturesRuntimeClasspath" ||
            it.name == "debugTestFixturesUnitTestCompileClasspath" ||
            it.name == "debugTestFixturesUnitTestRuntimeClasspath"
    }
        .configureEach {
            attributes {
                attribute(
                    TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE,
                    project.objects.named(TargetJvmEnvironment::class.java, TargetJvmEnvironment.STANDARD_JVM),
                )
            }
        }
}

// BundledSQLiteDriver loads a native library via System.loadLibrary, which JEP 472 flags as a
// "restricted method" on JDK 24+; this only silences that console warning, it changes no behavior.
tasks.withType<Test>().configureEach {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
