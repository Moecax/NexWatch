import org.gradle.api.attributes.java.TargetJvmEnvironment

plugins {
    id("nexwatch.android.library")
    id("nexwatch.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.nexwatch.core.export"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:common"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(testFixtures(project(":core:database")))
}

// Mirrors :core:data's afterEvaluate block: this module's JVM unit tests pull in
// testFixtures(":core:database")'s inMemoryTestDatabase(), which needs the standard-jvm
// Room/sqlite variant, not the android one (see core/database/build.gradle.kts for the full
// explanation of why the two variants disagree on a plain desktop JVM test run).
afterEvaluate {
    configurations.matching { it.name == "debugUnitTestCompileClasspath" || it.name == "debugUnitTestRuntimeClasspath" }
        .configureEach {
            attributes {
                attribute(
                    TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE,
                    project.objects.named(TargetJvmEnvironment::class.java, TargetJvmEnvironment.STANDARD_JVM),
                )
            }
        }
}

tasks.withType<Test>().configureEach {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
