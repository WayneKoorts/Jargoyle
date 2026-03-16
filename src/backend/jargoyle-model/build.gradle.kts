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
}
