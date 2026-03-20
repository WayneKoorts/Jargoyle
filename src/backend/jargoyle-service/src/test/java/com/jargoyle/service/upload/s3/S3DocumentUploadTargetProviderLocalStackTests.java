package com.jargoyle.service.upload.s3;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Testcontainers
class S3DocumentUploadTargetProviderLocalStackTests {

    private static final String BUCKET_NAME = "jargoyle-upload-target-tests";

    @Container
    static final LocalStackContainer LOCALSTACK = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.8.1"))
            .withServices(LocalStackContainer.Service.S3);

    private S3Client s3Client;
    private S3Presigner s3Presigner;

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

        s3Presigner = S3Presigner.builder()
                .endpointOverride(LOCALSTACK.getEndpointOverride(LocalStackContainer.Service.S3))
                .credentialsProvider(credentials)
                .region(Region.of(LOCALSTACK.getRegion()))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();

        s3Client.createBucket(CreateBucketRequest.builder().bucket(BUCKET_NAME).build());
    }

    @AfterEach
    void tearDown() {
        s3Presigner.close();
        s3Client.close();
    }

    @Test
    void createUploadTarget_generatesWorkingPresignedPutUrl() throws Exception {
        var properties = new S3StorageProperties(
                BUCKET_NAME,
                LOCALSTACK.getRegion(),
                LOCALSTACK.getEndpointOverride(LocalStackContainer.Service.S3),
                Duration.ofMinutes(15));
        var provider = new S3DocumentUploadTargetProvider(s3Presigner, properties);
        var descriptor = provider.createUploadTarget(UUID.randomUUID(), "contract.pdf");

        var uploadResponse = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(descriptor.uploadTarget().url()))
                        .PUT(HttpRequest.BodyPublishers.ofString("uploaded through presigned url"))
                        .build(),
                HttpResponse.BodyHandlers.discarding());

        assertThat(uploadResponse.statusCode()).isEqualTo(200);
        assertThat(descriptor.storageKey()).isNotBlank();

        var storedObject = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                .bucket(BUCKET_NAME)
                .key(descriptor.storageKey())
                .build());

        assertThat(storedObject.asUtf8String()).isEqualTo("uploaded through presigned url");
    }
}