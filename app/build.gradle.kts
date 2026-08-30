buildscript {
    // The Ktor Gradle plugin (io.ktor.plugin 3.5.2, via shadow/jib) drags
    // vulnerable transitives onto the buildscript/plugin classpath —
    // enforcedPlatform in the convention plugin's dependencies{} block cannot
    // reach it, so the same CVE floors are pinned again here for build time.
    dependencies {
        classpath(enforcedPlatform("com.fasterxml.jackson:jackson-bom:2.22.2"))
        classpath(enforcedPlatform("org.apache.logging.log4j:log4j-bom:2.26.1"))
        constraints {
            classpath("org.codehaus.plexus:plexus-utils:4.0.3") {
                because("GHSA-6fmv-xxpf-w3cw directory traversal; shadow drags 4.0.2")
            }
        }
    }
}

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
