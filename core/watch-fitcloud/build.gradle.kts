plugins {
    id("nexwatch.android.library")
    id("nexwatch.android.hilt")
}

android {
    namespace = "com.nexwatch.core.watchfitcloud"

    defaultConfig {
        // Travels with the module, so :app doesn't have to know the SDK's shape (§4).
        consumerProguardFiles("consumer-rules.pro")
    }
}

dependencies {
    implementation(project(":core:watch-api"))
    implementation(project(":core:common"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    // The SDK is RxJava3 end to end; this adapts it at the module boundary and nowhere
    // else, so no Rx type ever escapes into :core:data or the UI (§3.2).
    implementation(libs.kotlinx.coroutines.rx3)
    // FcSDK.Builder needs a ProcessLifecycleObserver; ProcessLifecycleOwner drives ours.
    implementation(libs.androidx.lifecycle.process)

    // third_party/maven/README.md: the vendored POMs declare no transitive dependencies on
    // purpose, so the SDK's runtime companions are pinned here instead of guessed from the
    // vendor's own POM. Both are load-bearing — the connector logs through Timber and drives
    // the radio through RxAndroidBLE, and the SDK crashes on init without them.
    implementation(libs.rxandroidble)
    implementation(libs.timber)

    // Vendored (third_party/maven/README.md).
    implementation("com.topstep.wearkit:sdk-base:3.0.2.4")
    implementation("com.topstep.wearkit:sdk-fitcloud:3.0.2.4")

    testImplementation(libs.kotlinx.coroutines.test)
}
