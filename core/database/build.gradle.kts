plugins {
    id("nexwatch.android.library")
}

android {
    namespace = "com.nexwatch.core.database"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
}
