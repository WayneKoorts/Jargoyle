plugins {
    id("io.spring.dependency-management") version "1.1.7" apply false
    id("org.springframework.boot") version "4.0.3" apply false
}

allprojects {
    group = "com.jargoyle"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
        maven { url = uri("https://repo.spring.io/milestone") }
    }
}

subprojects {
    apply(plugin = "io.spring.dependency-management")

    the<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension>().apply {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:4.0.3")
            mavenBom("org.testcontainers:testcontainers-bom:1.20.4")
            mavenBom("org.springframework.ai:spring-ai-bom:2.0.0-M2")
        }
    }

    plugins.withType<JavaPlugin> {
        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion = JavaLanguageVersion.of(25)
            }
        }

        tasks.withType<Test> {
            useJUnitPlatform()
            // Suppress Mockito deprecation warning for now.
            jvmArgs("-XX:+EnableDynamicAgentLoading")
        }
    }
}
