plugins {
    id("nexwatch.android.application")
    id("nexwatch.android.compose")
    id("nexwatch.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

// The Release workflow (.github/workflows/release.yml) passes the version it derived from the
// git tags; nothing in the repo stores it, so local builds are always 0.0.0-dev.
val releaseVersionName = providers.gradleProperty("nexwatch.versionName").orElse("0.0.0-dev").get()

// Monotonic as long as minor and patch stay below 1000.
fun versionCodeOf(versionName: String): Int {
    val match = Regex("""^(\d+)\.(\d+)\.(\d+)$""").find(versionName) ?: return 1
    val (major, minor, patch) = match.destructured.toList().map { it.toInt() }
    return major * 1_000_000 + minor * 1_000 + patch
}

// Set only in CI from repository secrets, so a release build without them comes out unsigned
// rather than signed with a throwaway key that couldn't install over a real release.
val releaseKeystore = providers.environmentVariable("NEXWATCH_KEYSTORE").orNull

android {
    namespace = "com.nexwatch"

    defaultConfig {
        applicationId = "com.nexwatch"
        versionCode = versionCodeOf(releaseVersionName)
        versionName = releaseVersionName
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = providers.environmentVariable("NEXWATCH_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("NEXWATCH_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("NEXWATCH_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            if (releaseKeystore != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            optimization {
                enable = true
                keepRules {
                    files.add(file("proguard-rules.pro"))
                }
            }
        }
    }

    buildFeatures {
        compose = true
        // The Watch debug screen shows its fake/real toggle only in debug builds.
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:watch-api"))
    implementation(project(":core:watch-fake"))
    implementation(project(":core:watch-fitcloud"))
    implementation(project(":core:common"))
    implementation(project(":core:data"))
    implementation(project(":core:export"))
    implementation(project(":core:service"))
    implementation(project(":core:sync-api"))
    implementation(project(":core:sync-healthconnect"))
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:today"))
    implementation(project(":feature:health"))
    implementation(project(":feature:watch"))
    implementation(platform(libs.androidx.compose.bom))
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    implementation(libs.kotlinx.serialization.json)
    ksp(libs.androidx.hilt.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
