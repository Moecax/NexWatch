plugins {
    id("nexwatch.android.library")
    id("nexwatch.android.compose")
}

android {
    namespace = "com.nexwatch.core.designsystem"
}

dependencies {
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
}
