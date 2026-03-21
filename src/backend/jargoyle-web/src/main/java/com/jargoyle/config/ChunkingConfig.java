package com.jargoyle.config;

import com.jargoyle.service.properties.ChunkingProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers chunking-related configuration properties with the Spring container.
 */
@Configuration
@EnableConfigurationProperties(ChunkingProperties.class)
public class ChunkingConfig {
}