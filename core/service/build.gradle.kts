plugins {
    id("nexwatch.android.library")
    id("nexwatch.android.hilt")
}

android {
    namespace = "com.nexwatch.core.service"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:watch-api"))
    implementation(project(":core:common"))
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
