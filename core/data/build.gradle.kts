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
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.junit)
}
