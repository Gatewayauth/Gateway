plugins {
    id("gateway.kotlin-common")
}

dependencies {
    api(libs.kotlinx.datetime)

    testImplementation(kotlin("test"))
}
