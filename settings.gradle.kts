pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        create("ktorLibs").from("io.ktor:ktor-version-catalog:3.5.2")
    }
}

includeBuild("build-logic")

rootProject.name = "gateway"

include(
    ":detekt-rules",
    ":common",
    ":domain",
    ":persistence",
    ":session",
    ":audit",
    ":auth-local",
    ":auth-external",
    ":mfa",
    ":oidc-provider",
    ":admin-api",
    ":app",
)
