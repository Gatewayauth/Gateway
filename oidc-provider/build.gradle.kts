plugins {
    id("gateway.kotlin-common")
}

dependencies {
    api(project(":domain"))
    implementation(project(":common"))
    implementation(project(":session"))
    implementation(libs.nimbus.jose.jwt)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}
