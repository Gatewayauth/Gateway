plugins {
    id("gateway.kotlin-common")
}

dependencies {
    api(project(":domain"))
    implementation(project(":common"))
    implementation(libs.totp)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}
