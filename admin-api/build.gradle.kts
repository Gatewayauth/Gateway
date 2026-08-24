plugins {
    id("gateway.kotlin-common")
}

dependencies {
    api(project(":domain"))
    implementation(project(":common"))

    testImplementation(kotlin("test"))
}
