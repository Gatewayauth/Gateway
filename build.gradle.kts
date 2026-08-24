import org.gradle.testing.jacoco.plugins.JacocoCoverageReport

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.detekt)
    id("jacoco-report-aggregation")
}

group = "io.gateway"
version = "1.0.0-SNAPSHOT"

// Aggregate JaCoCo coverage across every module into a single report at
// build/reports/jacoco/testCodeCoverageReport/. Run: ./gradlew testCodeCoverageReport
dependencies {
    jacocoAggregation(project(":common"))
    jacocoAggregation(project(":domain"))
    jacocoAggregation(project(":persistence"))
    jacocoAggregation(project(":session"))
    jacocoAggregation(project(":audit"))
    jacocoAggregation(project(":auth-local"))
    jacocoAggregation(project(":auth-external"))
    jacocoAggregation(project(":mfa"))
    jacocoAggregation(project(":oidc-provider"))
    jacocoAggregation(project(":admin-api"))
    jacocoAggregation(project(":app"))
}

reporting {
    reports {
        val testCodeCoverageReport by creating(JacocoCoverageReport::class) {
            testSuiteName.set("test")
        }
    }
}

// Convenience: run detekt across all modules from the root.
tasks.register("detektAll") {
    group = "verification"
    description = "Runs detekt on all modules."
    dependsOn(subprojects.map { "${it.path}:detekt" })
}
