plugins {
    java
    id("org.springframework.boot")
}

dependencies {
    // Sub-projects
    implementation(project(":jargoyle-model"))
    implementation(project(":jargoyle-repository"))
    implementation(project(":jargoyle-service"))

    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Web
    implementation("org.springframework.boot:spring-boot-starter-web")

    // Persistence
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("org.postgresql:postgresql")

    // Database migrations
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")

    // Security
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")

    // Validation
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // API docs
    // Source: https://mvnrepository.com/artifact/org.springdoc/springdoc-openapi-starter-webmvc-ui
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.1")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        // Prevent accidental importing of JUnit 4 classes.
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
