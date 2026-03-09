plugins {
    `java-library`
}

dependencies {
    api(project(":jargoyle-model"))
    implementation(project(":jargoyle-repository"))

    // Spring framework
    implementation("org.springframework:spring-context")
    implementation("org.springframework:spring-core")

    // OAuth2 for CustomOidcUserService and AuthenticatedUserResolver
    implementation("org.springframework.security:spring-security-oauth2-client")

    // @ConfigurationProperties for DocumentProcessingProperties
    implementation("org.springframework.boot:spring-boot")

    // PDF text extraction
    implementation("org.apache.pdfbox:pdfbox:3.0.6")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
