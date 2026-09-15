plugins {
    id("nexwatch.android.library")
}

android {
    namespace = "com.nexwatch.core.watchfitcloud"
}

dependencies {
    implementation(project(":core:watch-api"))
    implementation(project(":core:common"))

    // Vendored (third_party/maven/README.md). The FitCloudWatchClient
    // implementation itself is Phase 4 — this just proves the vendored
    // artifacts resolve and pass Gradle dependency verification.
    implementation("com.topstep.wearkit:sdk-base:3.0.2.4")
    implementation("com.topstep.wearkit:sdk-fitcloud:3.0.2.4")
}
