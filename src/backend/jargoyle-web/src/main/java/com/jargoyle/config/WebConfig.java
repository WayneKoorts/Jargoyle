package com.jargoyle.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.jargoyle.config.properties.MvcAsyncProperties;
import com.jargoyle.controller.CurrentUserArgumentResolver;

/**
 * Web MVC configuration for argument resolvers and async support.
 *
 * <p>Registers a thread pool executor for MVC async request handling
 * (SSE streaming endpoints). Without this, Spring uses the default
 * {@code SimpleAsyncTaskExecutor} which is not suitable for production.</p>
 */
@Configuration
@EnableConfigurationProperties(MvcAsyncProperties.class)
public class WebConfig implements WebMvcConfigurer {

    private final CurrentUserArgumentResolver currentUserArgumentResolver;
    private final MvcAsyncProperties mvcAsyncProperties;
    private final AsyncTaskExecutor mvcAsyncExecutor;

    public WebConfig(CurrentUserArgumentResolver currentUserArgumentResolver,
                     MvcAsyncProperties mvcAsyncProperties,
                     @Qualifier("mvcAsyncExecutor") AsyncTaskExecutor mvcAsyncExecutor) {
        this.currentUserArgumentResolver = currentUserArgumentResolver;
        this.mvcAsyncProperties = mvcAsyncProperties;
        this.mvcAsyncExecutor = mvcAsyncExecutor;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserArgumentResolver);
    }

    /**
     * Configures a bounded thread pool for MVC async requests (SSE streams).
     * This replaces the default {@code SimpleAsyncTaskExecutor} which creates
     * an unbounded number of threads per request.
     */
    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(mvcAsyncExecutor);
        configurer.setDefaultTimeout(mvcAsyncProperties.timeoutMillis());
    }

}
