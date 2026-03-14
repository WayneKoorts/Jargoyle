package com.jargoyle.config;

import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI jargoyleOpenApi() {
        return new OpenAPI()
            .info(new Info()
                .title("Jargoyle API")
                .description("Document explanation tool - upload documents and get plain-English summaries."));
    }

    @Bean
    public OperationCustomizer hideCurrentUserParam() {
        return (operation, handlerMethod) -> {
            if (operation.getParameters() != null) {
                operation.getParameters().removeIf(p -> "user".equals(p.getName()));
            }

            return operation;
        };
    }

}
