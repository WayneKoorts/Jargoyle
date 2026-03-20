plugins {
    `java-library`
}

dependencies {
    api(project(":jargoyle-model"))
    implementation(project(":jargoyle-repository"))

    // Spring framework
    implementation("org.springframework:spring-context")
    implementation("org.springframework:spring-core")
    implementation("org.springframework.ai:spring-ai-starter-model-openai")

    // OAuth2 for CustomOidcUserService and AuthenticatedUserResolver
    implementation("org.springframework.security:spring-security-oauth2-client")

    // @ConfigurationProperties for DocumentProcessingProperties
    implementation("org.springframework.boot:spring-boot")

    // PDF text extraction
    implementation("org.apache.pdfbox:pdfbox:3.0.6")

    // AWS S3 (async client for non-blocking storage)
    implementation("software.amazon.awssdk:s3")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:localstack")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
