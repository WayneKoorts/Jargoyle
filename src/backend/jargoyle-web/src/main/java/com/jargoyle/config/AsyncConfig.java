package com.jargoyle.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.jargoyle.config.properties.MvcAsyncProperties;
import com.jargoyle.service.properties.DocumentProcessingProperties;

/**
 * Async executor configuration.
 *
 * <p>Defines all {@link ThreadPoolTaskExecutor} beans so Spring manages their
 * lifecycle (including clean shutdown of worker threads).</p>
 */
@Configuration
@EnableAsync
@EnableConfigurationProperties(DocumentProcessingProperties.class)
public class AsyncConfig {

    /** Executor for background document processing ({@code @Async} methods). */
    @Bean(name = "documentProcessingExecutor")
    public TaskExecutor documentProcessingExecutor(DocumentProcessingProperties props) {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(props.corePoolSize());
        executor.setMaxPoolSize(props.maxPoolSize());
        executor.setQueueCapacity(props.queueCapacity());
        executor.setThreadNamePrefix(props.threadNamePrefix());
        return executor;
    }

    /**
     * Executor for MVC async requests (SSE streaming endpoints).
     *
     * <p>Replaces the default {@code SimpleAsyncTaskExecutor} which creates
     * an unbounded number of threads per request. Injected into
     * {@link WebConfig#configureAsyncSupport}.</p>
     */
    @Bean(name = "mvcAsyncExecutor")
    public AsyncTaskExecutor mvcAsyncExecutor(MvcAsyncProperties props) {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(props.corePoolSize());
        executor.setMaxPoolSize(props.maxPoolSize());
        executor.setQueueCapacity(props.queueCapacity());
        executor.setThreadNamePrefix(props.threadNamePrefix());
        return executor;
    }

}
