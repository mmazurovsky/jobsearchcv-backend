import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.6"
    id("io.sentry.jvm.gradle") version "5.8.0"
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.spring") version "2.2.0"
    kotlin("plugin.serialization") version "2.2.0"
    idea
}

group = "com.jobsearchcv.backend"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
}

sentry {
    // Generates a JVM (Java, Kotlin, etc.) source bundle and uploads your source code to Sentry.
    // This enables source context, allowing you to see your source
    // code as part of your stack traces in Sentry.
    includeSourceContext = false

    org = "self-a04"
    projectName = "job-search-cv-backend"
//    authToken = System.getenv("SENTRY_AUTH_TOKEN")
}

val serialization = "1.9.0"
val datetime = "0.7.1-0.6.x-compat"
val supabase = "3.2.2"
val ktorVersion = "3.2.2"
val logbookVersion = "3.7.0"



configurations.all {
    resolutionStrategy {
        force(
            "org.jetbrains.kotlinx:kotlinx-serialization-core:$serialization",
            "org.jetbrains.kotlinx:kotlinx-serialization-json:$serialization",
            "org.jetbrains.kotlinx:kotlinx-serialization-properties:$serialization",
            "org.jetbrains.kotlinx:kotlinx-serialization-cbor:$serialization",
            "org.jetbrains.kotlinx:kotlinx-datetime:$datetime"
        )
    }
}
dependencies {
    // ── Spring Boot ──────────────────────────────────────────────
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb-reactive")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-quartz")

    // ── Logging (Logback with SLF4J - industry standard) ─────────
    // Spring Boot includes Logback by default - no additional dependencies needed
    // Add structured logging for production
    implementation("net.logstash.logback:logstash-logback-encoder:7.4")
    // Add request/response logging (Jakarta EE for Spring Boot 3)
    implementation("org.zalando:logbook-spring-boot-starter:$logbookVersion")
    implementation("org.zalando:logbook-servlet:$logbookVersion")
    
    // ── Sentry Error Tracking ───────────────────────────────────
    implementation("io.sentry:sentry-spring-boot-starter-jakarta:8.14.0")
    implementation("io.sentry:sentry-logback:8.14.0")

    // ── Kotlin / Coroutines, Serialization ──────────────────────────────────────
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    implementation(enforcedPlatform("org.jetbrains.kotlinx:kotlinx-serialization-bom:$serialization"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json")   // no version
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core")   // no version
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:${datetime}")

    // ── HTTP (Ktor 2) ────────────────────────────────────────────

    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    
    // ── AWS SDK v2 ───────────────────────────────────────────────
    implementation(platform("software.amazon.awssdk:bom:2.25.0"))
    implementation("software.amazon.awssdk:s3")
    implementation("software.amazon.awssdk:s3-transfer-manager")
    
    // ── Resend Email SDK ────────────────────────────────────────
    implementation("com.resend:resend-java:4.5.0")
    
    // ── Document Processing ─────────────────────────────────────
    implementation("org.apache.pdfbox:pdfbox:3.0.3")
    implementation("org.apache.poi:poi:5.2.5")
    implementation("org.apache.poi:poi-scratchpad:5.2.5") // For .doc files
    implementation("org.apache.poi:poi-ooxml:5.2.5") // For .docx files

    // ── Security & JWT ─────────────────────────────────────────
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("io.jsonwebtoken:jjwt-api:0.12.5")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.5")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.5")
    
    // ── OpenAPI Documentation ──────────────────────────────────
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.3.0")
    
    // ── Supabase ──────────────────────────────────────────────
    implementation("io.github.jan-tennert.supabase:supabase-kt-jvm:$supabase")
    implementation("io.github.jan-tennert.supabase:auth-kt-jvm:$supabase")
    implementation(platform("io.github.jan-tennert.supabase:bom:$supabase"))

    // ── Configuration processing ────────────────────────────────
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // ── Testing ─────────────────────────────────────────────────
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    testImplementation("org.testcontainers:junit-jupiter:1.19.3")
    testImplementation("org.testcontainers:mongodb:1.19.3")
    implementation(kotlin("stdlib-jdk8"))
}


tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

springBoot {
    buildInfo {
        properties {
            additional = mapOf(
                "description" to "Jobs Alerts Core Service - Kotlin/Spring Boot Implementation"
            )
        }
    }
} 