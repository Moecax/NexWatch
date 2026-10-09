plugins {
    id("nexwatch.android.library")
    id("nexwatch.android.hilt")
}

android {
    namespace = "com.nexwatch.core.sync.healthconnect"
}

dependencies {
    implementation(project(":core:sync-api"))
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(libs.androidx.health.connect.client)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
