plugins {
    id("nexwatch.android.library")
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
}
