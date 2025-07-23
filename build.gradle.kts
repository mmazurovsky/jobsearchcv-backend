import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.6"
    id("io.sentry.jvm.gradle") version "5.8.0"
    kotlin("jvm") version "2.0.20"
    kotlin("plugin.spring") version "2.0.20"
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
    includeSourceContext = true

    org = "self-a04"
    projectName = "job-search-cv-backend"
//    authToken = System.getenv("SENTRY_AUTH_TOKEN")
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
    
    // ── Sentry Error Tracking ───────────────────────────────────
    implementation("io.sentry:sentry-spring-boot-starter-jakarta:8.14.0")
    implementation("io.sentry:sentry-logback:8.14.0")

    // ── Kotlin / Coroutines ──────────────────────────────────────
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

    // ── Telegram and other social media ─────────────────────────────────────────────
    implementation("dev.inmo:tgbotapi:15.2.0")

    // ── HTTP (Ktor 2) ────────────────────────────────────────────
    val ktorVersion = "2.3.12"
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    
    // ── AWS SDK v2 ───────────────────────────────────────────────
    implementation(platform("software.amazon.awssdk:bom:2.25.0"))
    implementation("software.amazon.awssdk:s3")
    implementation("software.amazon.awssdk:s3-transfer-manager")
    
    // ── Document Processing ─────────────────────────────────────
    implementation("org.apache.pdfbox:pdfbox:3.0.3")
    implementation("org.apache.poi:poi:5.2.5")
    implementation("org.apache.poi:poi-scratchpad:5.2.5") // For .doc files
    implementation("org.apache.poi:poi-ooxml:5.2.5") // For .docx files

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
}


tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
    kotlinOptions {
        jvmTarget = "21"
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