plugins {
    id("gateway.kotlin-common")
}

dependencies {
    api(project(":domain"))

    testImplementation(kotlin("test"))
}
