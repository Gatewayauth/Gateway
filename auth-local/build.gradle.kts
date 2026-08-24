plugins {
    id("gateway.kotlin-common")
}

dependencies {
    api(project(":domain"))
    implementation(project(":common"))
    implementation(libs.argon2)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}
