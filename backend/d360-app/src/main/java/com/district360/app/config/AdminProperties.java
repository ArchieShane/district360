package com.district360.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "d360.admin")
public record AdminProperties(String token) {
}
