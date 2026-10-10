plugins {
    id("nexwatch.android.library")
    id("nexwatch.android.compose")
    id("nexwatch.android.hilt")
}

android {
    namespace = "com.nexwatch.feature.watch"
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:watch-api"))
    implementation(project(":core:data"))
    implementation(project(":core:service"))
    implementation(project(":core:common"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.kotlinx.coroutines.core)
    ksp(libs.androidx.hilt.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(project(":core:watch-fake"))
    debugImplementation(libs.androidx.compose.ui.tooling)
}
