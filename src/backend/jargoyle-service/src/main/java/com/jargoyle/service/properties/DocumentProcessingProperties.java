package com.jargoyle.service.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jargoyle.async.document-processing")
public record DocumentProcessingProperties(
    int corePoolSize,
    int maxPoolSize,
    int queueCapacity,
    String threadNamePrefix
) {
    public DocumentProcessingProperties {
        // defaults
        if (corePoolSize <= 0) corePoolSize = 2;
        if (maxPoolSize <= 0) maxPoolSize = 5;
        if (queueCapacity <= 0) queueCapacity = 25;
        if (threadNamePrefix == null) threadNamePrefix = "doc-processing";
    }
}
