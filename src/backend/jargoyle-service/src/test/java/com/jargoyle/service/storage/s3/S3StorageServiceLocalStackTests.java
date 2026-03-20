package com.jargoyle.service.storage.s3;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.jargoyle.service.properties.S3StorageProperties;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

@Testcontainers
class S3StorageServiceLocalStackTests {

    private static final String BUCKET_NAME = "jargoyle-storage-tests";

    @Container
    static final LocalStackContainer LOCALSTACK = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.8.1"))
            .withServices(LocalStackContainer.Service.S3);

    private S3Client s3Client;
    private S3AsyncClient s3AsyncClient;

    @BeforeEach
    void setUp() {
        var credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create(
                LOCALSTACK.getAccessKey(),
                LOCALSTACK.getSecretKey()));

        s3Client = S3Client.builder()
                .endpointOverride(LOCALSTACK.getEndpointOverride(LocalStackContainer.Service.S3))
                .credentialsProvider(credentials)
                .region(Region.of(LOCALSTACK.getRegion()))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();

        s3AsyncClient = S3AsyncClient.builder()
                .endpointOverride(LOCALSTACK.getEndpointOverride(LocalStackContainer.Service.S3))
                .credentialsProvider(credentials)
                .region(Region.of(LOCALSTACK.getRegion()))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();

        s3Client.createBucket(CreateBucketRequest.builder().bucket(BUCKET_NAME).build());
    }

    @AfterEach
    void tearDown() {
        s3AsyncClient.close();
        s3Client.close();
    }

    @Test
    void storeLoadExistsDelete_roundTripsAgainstLocalStack() throws Exception {
        var properties = new S3StorageProperties(
                BUCKET_NAME,
                LOCALSTACK.getRegion(),
                LOCALSTACK.getEndpointOverride(LocalStackContainer.Service.S3),
                Duration.ofMinutes(15));
        var storageService = new S3StorageService(s3AsyncClient, properties);
        var documentId = UUID.randomUUID();
        var content = "hello from s3";

        var storageKey = storageService.store(documentId, new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))).join();

        assertThat(storageService.exists(storageKey).join()).isTrue();
        try (var stream = storageService.load(storageKey).join().getInputStream()) {
            assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(content);
        }

        storageService.delete(storageKey).join();

        assertThat(storageService.exists(storageKey).join()).isFalse();
    }
}