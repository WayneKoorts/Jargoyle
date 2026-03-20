package com.jargoyle.service.properties;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds configuration from {@code jargoyle.storage.s3.*} to typed fields.
 * Only active in the {@code prod} profile (via {@link com.jargoyle.service.config.S3StorageConfig}).
 *
 * @param bucketName      the S3 bucket to store documents in
 * @param region          the AWS region (defaults to eu-west-2 in application-prod.yml)
 * @param endpointOverride optional custom endpoint, e.g. for LocalStack testing — null in production
 * @param uploadUrlTtl    how long presigned upload URLs remain valid
 */
@ConfigurationProperties(prefix = "jargoyle.storage.s3")
public record S3StorageProperties(
    String bucketName,
    String region,
    URI endpointOverride,
    Duration uploadUrlTtl
) {
    public S3StorageProperties {
        if (bucketName == null || bucketName.isBlank()) {
            throw new IllegalArgumentException("jargoyle.storage.s3.bucket-name must not be blank");
        }
        if (region == null || region.isBlank()) {
            throw new IllegalArgumentException("jargoyle.storage.s3.region must not be blank");
        }
        if (uploadUrlTtl == null || uploadUrlTtl.isZero() || uploadUrlTtl.isNegative()) {
            throw new IllegalArgumentException("jargoyle.storage.s3.upload-url-ttl must be positive");
        }
    }
}
