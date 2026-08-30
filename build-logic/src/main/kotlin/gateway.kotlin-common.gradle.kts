import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("io.gitlab.arturbosch.detekt")
    jacoco
}

kotlin {
    jvmToolchain(21)
}

jacoco {
    toolVersion = "0.8.13"
}

tasks.withType<Test>().configureEach {
    finalizedBy(tasks.named("jacocoTestReport"))
}
tasks.withType<JacocoReport>().configureEach {
    dependsOn(tasks.named("test"))
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        // Bootstrapping against fast-moving libs (Exposed 1.x, kotlinx-datetime
        // compat) makes strict warnings-as-errors brittle. detekt is the quality
        // gate instead; revisit once dependencies stabilise.
        allWarningsAsErrors.set(false)
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

configure<DetektExtension> {
    buildUponDefaultConfig = true
    allRules = false
    parallel = true
    config.setFrom(rootProject.files("config/detekt/detekt.yml"))
}

tasks.withType<Detekt>().configureEach {
    jvmTarget = "21"
    reports {
        html.required.set(true)
        xml.required.set(true)
        sarif.required.set(true)
    }
}

dependencies {
    // Security floors for transitive CVEs (Dependabot). These libs are pulled in
    // transitively (Ktor/Lettuce/testcontainers/flyway/nimbus) with no direct
    // declaration, so we force patched versions here for every module. Remove
    // each entry once the upstream that drags it in ships the fixed version.
    add("implementation", enforcedPlatform("io.netty:netty-bom:4.2.17.Final"))
    add("implementation", enforcedPlatform("com.fasterxml.jackson:jackson-bom:2.22.2"))
    add("implementation", enforcedPlatform("org.apache.logging.log4j:log4j-bom:2.25.5"))
    constraints {
        add("implementation", "org.apache.httpcomponents.core5:httpcore5:5.4.3") {
            because("GHSA HTTP/1 header-parsing memory-exhaustion DoS")
        }
        add("implementation", "org.apache.httpcomponents.core5:httpcore5-h2:5.4.3") {
            because("GHSA HPACKDecoder unlimited header list DoS")
        }
        add("implementation", "org.apache.httpcomponents.client5:httpclient5:5.6.3") {
            because("GHSA connection-leak pool-exhaustion DoS")
        }
        add("implementation", "org.apache.commons:commons-compress:1.26.0") {
            because("GHSA DUMP/Pack200 DoS")
        }
        add("implementation", "org.codehaus.plexus:plexus-utils:4.0.3") {
            because("GHSA directory traversal in extractFile")
        }
    }

    "detektPlugins"("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.8")
    "detektPlugins"(project(":detekt-rules"))
}
