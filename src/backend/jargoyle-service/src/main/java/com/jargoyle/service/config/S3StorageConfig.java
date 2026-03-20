package com.jargoyle.service.config;

import com.jargoyle.service.properties.S3StorageProperties;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Creates the {@link S3AsyncClient} bean used by the S3 storage implementation.
 * Profile-gated to {@code prod} so the S3 properties aren't validated during local
 * development (where {@link com.jargoyle.service.storage.local.FileSystemStorageService}
 * is used instead).
 *
 * <p>The async client uses the AWS default credential provider chain, which picks up
 * credentials from environment variables, IAM roles, or the ~/.aws/credentials file
 * automatically — no explicit key configuration needed.
 */
@Configuration
@Profile("prod")
@EnableConfigurationProperties(S3StorageProperties.class)
public class S3StorageConfig {

    @Bean
    public S3AsyncClient s3AsyncClient(S3StorageProperties properties) {
        var builder = S3AsyncClient.builder()
                .region(Region.of(properties.region()));

        if (properties.endpointOverride() != null) {
            builder.endpointOverride(properties.endpointOverride());
            builder.serviceConfiguration(pathStyleConfig());
        }

        return builder.build();
    }

    @Bean
    public S3Presigner s3Presigner(S3StorageProperties properties) {
        var builder = S3Presigner.builder()
                .region(Region.of(properties.region()));

        if (properties.endpointOverride() != null) {
            builder.endpointOverride(properties.endpointOverride());
            builder.serviceConfiguration(pathStyleConfig());
        }

        return builder.build();
    }

    private static S3Configuration pathStyleConfig() {
        return S3Configuration.builder().pathStyleAccessEnabled(true).build();
    }
}
