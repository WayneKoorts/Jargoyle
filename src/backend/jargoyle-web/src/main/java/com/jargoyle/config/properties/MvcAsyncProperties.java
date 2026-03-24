package com.jargoyle.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Spring MVC async task executor.
 *
 * <p>This executor handles asynchronous MVC requests such as SSE streaming
 * endpoints ({@code Flux} and {@code SseEmitter} responses). Without it,
 * Spring falls back to {@code SimpleAsyncTaskExecutor} which creates an
 * unbounded number of threads — unsuitable for production use.</p>
 */
@ConfigurationProperties(prefix = "jargoyle.async.mvc")
public record MvcAsyncProperties(
    int corePoolSize,
    int maxPoolSize,
    int queueCapacity,
    String threadNamePrefix,
    long timeoutMillis
) {
    public MvcAsyncProperties {
        if (corePoolSize <= 0) corePoolSize = 5;
        if (maxPoolSize <= 0) maxPoolSize = 20;
        if (queueCapacity <= 0) queueCapacity = 100;
        if (threadNamePrefix == null) threadNamePrefix = "mvc-async-";
        if (timeoutMillis <= 0) timeoutMillis = 300_000;
    }
}
