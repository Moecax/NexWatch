plugins {
    id("nexwatch.android.library")
}

android {
    namespace = "com.nexwatch.core.service"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:watch-api"))
    implementation(project(":core:common"))
}
