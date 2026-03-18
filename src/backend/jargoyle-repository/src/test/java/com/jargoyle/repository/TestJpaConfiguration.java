package com.jargoyle.repository;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;

// Minimal class needed for auto config.
@SpringBootApplication
@EntityScan("com.jargoyle.entity")
public class TestJpaConfiguration {}
