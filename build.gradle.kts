import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "3.3.4"
    id("io.spring.dependency-management") version "1.1.6"
    kotlin("jvm") version "2.0.20"
    kotlin("plugin.spring") version "2.0.20"
    kotlin("plugin.jpa") version "2.0.20"
}

java.toolchain {
    languageVersion = JavaLanguageVersion.of(21)
}

repositories {
    mavenCentral()
    // 追加官方鏡像以降低 Maven Central 偶發 403 風險
    maven(url = "https://repo1.maven.org/maven2/")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    // OpenAPI/Swagger UI for auto-generated API docs
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")
    // HTTP client utilities (already in Spring), JSON handled by Jackson
    implementation("org.hibernate.orm:hibernate-community-dialects:6.5.3.Final")
    runtimeOnly("org.xerial:sqlite-jdbc:3.45.3.0")
    implementation("org.flywaydb:flyway-core")
    // Testing dependencies
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.mockito") // 使用 MockK 取代 Mockito
    }
    testImplementation("io.mockk:mockk:1.13.9") // MockK for Kotlin
    testImplementation("com.ninja-squad:springmockk:4.0.2") // Spring + MockK 整合
    testImplementation("io.kotest:kotest-runner-junit5:5.8.0") // Kotest JUnit5 runner
    testImplementation("io.kotest:kotest-assertions-core:5.8.0") // Kotest assertions
    testImplementation("io.kotest:kotest-property:5.8.0") // Property-based testing
    testImplementation("io.kotest.extensions:kotest-extensions-spring:1.1.3") // Kotest Spring extension
    testImplementation("com.h2database:h2") // H2 for in-memory database testing
    testImplementation("org.testcontainers:testcontainers:1.19.3") // Testcontainers (可選)
    testImplementation("org.testcontainers:junit-jupiter:1.19.3") // Testcontainers JUnit5
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += "-Xjsr305=strict"
        jvmTarget = "21"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
