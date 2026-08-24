plugins {
    id("gateway.kotlin-common")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":domain"))
    implementation(project(":common"))

    implementation(ktorLibs.client.core)
    implementation(ktorLibs.client.cio)
    implementation(ktorLibs.client.contentNegotiation)
    implementation(ktorLibs.serialization.kotlinx.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}
