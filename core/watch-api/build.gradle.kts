plugins {
    id("nexwatch.jvm.library")
}

dependencies {
    api(project(":core:model"))
    api(libs.kotlinx.coroutines.core)
}
