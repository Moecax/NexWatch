plugins {
    id("nexwatch.android.library")
}

android {
    namespace = "com.nexwatch.core.watchfitcloud"
}

dependencies {
    implementation(project(":core:watch-api"))
    implementation(project(":core:common"))
}
