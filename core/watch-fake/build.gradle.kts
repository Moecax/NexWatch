plugins {
    id("nexwatch.android.library")
}

android {
    namespace = "com.nexwatch.core.watchfake"
}

dependencies {
    implementation(project(":core:watch-api"))
}
