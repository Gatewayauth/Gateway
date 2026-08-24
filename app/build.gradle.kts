plugins {
    id("gateway.kotlin-common")
    alias(libs.plugins.kotlin.serialization)
    alias(ktorLibs.plugins.ktor)
}

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

dependencies {
    implementation(project(":common"))
    implementation(project(":domain"))
    implementation(project(":persistence"))
    implementation(project(":session"))
    implementation(project(":audit"))
    implementation(project(":auth-local"))
    implementation(project(":auth-external"))
    implementation(project(":mfa"))
    implementation(project(":oidc-provider"))
    implementation(project(":admin-api"))

    implementation(ktorLibs.server.core)
    implementation(ktorLibs.server.netty)
    implementation(ktorLibs.server.contentNegotiation)
    implementation(ktorLibs.serialization.kotlinx.json)
    implementation(ktorLibs.server.callId)
    implementation(ktorLibs.server.callLogging)
    implementation(ktorLibs.server.statusPages)
    implementation(ktorLibs.server.defaultHeaders)
    implementation(ktorLibs.server.forwardedHeader)
    implementation(ktorLibs.server.cors)
    implementation(ktorLibs.server.sessions)
    implementation(ktorLibs.server.requestValidation)
    implementation(ktorLibs.server.autoHeadResponse)
    implementation(ktorLibs.server.swagger)
    implementation(ktorLibs.server.rateLimit)

    implementation(ktorLibs.client.core)
    implementation(ktorLibs.client.cio)
    implementation(ktorLibs.client.contentNegotiation)

    implementation(libs.koin.ktor)
    implementation(libs.koin.logger.slf4j)
    implementation(libs.lettuce.core)
    implementation(libs.cohort.cohortKtor)
    implementation(libs.logback.classic)
    implementation(libs.angus.mail)
    runtimeOnly(libs.h2database.h2)

    testImplementation(kotlin("test"))
    testImplementation(ktorLibs.server.testHost)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.totp)
    testImplementation(libs.mockk)
}
