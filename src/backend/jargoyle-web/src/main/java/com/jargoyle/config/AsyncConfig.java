package com.jargoyle.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.jargoyle.service.properties.DocumentProcessingProperties;

@Configuration
@EnableAsync
@EnableConfigurationProperties(DocumentProcessingProperties.class)
public class AsyncConfig {

    @Bean(name = "documentProcessingExecutor")
    public TaskExecutor documentProcessingExecutor(DocumentProcessingProperties props) {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(props.corePoolSize());
        executor.setMaxPoolSize(props.maxPoolSize());
        executor.setQueueCapacity(props.queueCapacity());
        executor.setThreadNamePrefix(props.threadNamePrefix());
        return executor;
    }

}
