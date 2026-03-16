plugins {
    `java-library`
}

dependencies {
    // JPA entity annotations
    api("jakarta.persistence:jakarta.persistence-api")

    // Validation annotations for DTOs
    api("jakarta.validation:jakarta.validation-api")

    // Hibernate annotations (@CreationTimestamp, @UpdateTimestamp)
    implementation("org.hibernate.orm:hibernate-core")

    implementation("org.postgresql:postgresql")

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
