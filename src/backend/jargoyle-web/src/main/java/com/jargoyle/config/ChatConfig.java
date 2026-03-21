package com.jargoyle.config;

import com.jargoyle.service.properties.ChatProperties;
import com.jargoyle.service.properties.RetrievalProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers chat-related configuration properties with the Spring container.
 */
@Configuration
@EnableConfigurationProperties({RetrievalProperties.class, ChatProperties.class})
public class ChatConfig {
}
