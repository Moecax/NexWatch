plugins {
    id("nexwatch.android.library")
}

android {
    namespace = "com.nexwatch.core.export"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:data"))
}
