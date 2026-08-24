plugins {
    id("gateway.kotlin-common")
}

dependencies {
    api(project(":common"))
    api(libs.kotlinx.datetime)
    api(libs.kotlinx.coroutines.core)

    testImplementation(kotlin("test"))
}
