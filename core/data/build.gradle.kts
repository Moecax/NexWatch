import org.gradle.api.attributes.java.TargetJvmEnvironment

plugins {
    id("nexwatch.android.library")
    id("nexwatch.android.hilt")
}

android {
    namespace = "com.nexwatch.core.data"
}

dependencies {
    implementation(project(":core:database"))
    implementation(project(":core:watch-api"))
    implementation(project(":core:sync-api"))
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.paging.runtime)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.junit)
    testImplementation(project(":core:database"))
    testImplementation(libs.androidx.sqlite.bundled)
    testImplementation(libs.androidx.room.runtime)
}

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
