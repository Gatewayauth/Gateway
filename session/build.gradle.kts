plugins {
    id("gateway.kotlin-common")
}

dependencies {
    api(project(":domain"))
    implementation(project(":common"))

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}
