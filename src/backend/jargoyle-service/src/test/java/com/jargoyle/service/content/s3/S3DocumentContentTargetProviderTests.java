package com.jargoyle.service.content.s3;

import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.jargoyle.service.properties.S3StorageProperties;

import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class S3DocumentContentTargetProviderTests {

    private S3Presigner mockPresigner;
    private S3DocumentContentTargetProvider sut;

    private static final UUID DOCUMENT_ID = UUID.randomUUID();
    private static final String STORAGE_KEY = DOCUMENT_ID + "/" + UUID.randomUUID();
    private static final String BUCKET_NAME = "test-bucket";
    private static final Duration URL_TTL = Duration.ofMinutes(15);

    @BeforeEach
    void setUp() {
        mockPresigner = mock(S3Presigner.class);
        var properties = new S3StorageProperties(BUCKET_NAME, "eu-west-2", null, URL_TTL);
        sut = new S3DocumentContentTargetProvider(mockPresigner, properties);
    }

    @Test
    void createContentUrl_generatesPresignedGetUrl() throws Exception {
        var presignedUrl = URI.create("https://s3.eu-west-2.amazonaws.com/%s/%s?presigned".formatted(BUCKET_NAME, STORAGE_KEY)).toURL();
        var mockPresignedRequest = mock(PresignedGetObjectRequest.class);
        when(mockPresignedRequest.url()).thenReturn(presignedUrl);
        when(mockPresigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenReturn(mockPresignedRequest);

        var result = sut.createContentUrl(DOCUMENT_ID, STORAGE_KEY, "contract.pdf");

        assertThat(result).isEqualTo(presignedUrl.toString());
    }

    @Test
    void createContentUrl_presignRequestUsesCorrectBucketAndKey() throws Exception {
        var presignedUrl = URI.create("https://s3.example.com/presigned").toURL();
        var mockPresignedRequest = mock(PresignedGetObjectRequest.class);
        when(mockPresignedRequest.url()).thenReturn(presignedUrl);

        var captor = ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        when(mockPresigner.presignGetObject(captor.capture()))
                .thenReturn(mockPresignedRequest);

        sut.createContentUrl(DOCUMENT_ID, STORAGE_KEY, "contract.pdf");

        var captured = captor.getValue();
        assertThat(captured.getObjectRequest().bucket()).isEqualTo(BUCKET_NAME);
        assertThat(captured.getObjectRequest().key()).isEqualTo(STORAGE_KEY);
        assertThat(captured.signatureDuration()).isEqualTo(URL_TTL);
    }

    @Test
    void createContentUrl_setsInlineContentDispositionWithFilename() throws Exception {
        var presignedUrl = URI.create("https://s3.example.com/presigned").toURL();
        var mockPresignedRequest = mock(PresignedGetObjectRequest.class);
        when(mockPresignedRequest.url()).thenReturn(presignedUrl);

        var captor = ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        when(mockPresigner.presignGetObject(captor.capture()))
                .thenReturn(mockPresignedRequest);

        sut.createContentUrl(DOCUMENT_ID, STORAGE_KEY, "my-document.pdf");

        var contentDisposition = captor.getValue().getObjectRequest().responseContentDisposition();
        assertThat(contentDisposition).contains("inline");
        assertThat(contentDisposition).contains("my-document.pdf");
    }

    @Test
    void createContentUrl_nullFilename_setsInlineWithoutFilename() throws Exception {
        var presignedUrl = URI.create("https://s3.example.com/presigned").toURL();
        var mockPresignedRequest = mock(PresignedGetObjectRequest.class);
        when(mockPresignedRequest.url()).thenReturn(presignedUrl);

        var captor = ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        when(mockPresigner.presignGetObject(captor.capture()))
                .thenReturn(mockPresignedRequest);

        sut.createContentUrl(DOCUMENT_ID, STORAGE_KEY, null);

        var contentDisposition = captor.getValue().getObjectRequest().responseContentDisposition();
        assertThat(contentDisposition).isEqualTo("inline");
    }
}
