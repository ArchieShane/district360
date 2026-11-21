package com.district360.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "d360.etl")
public record EtlProperties(boolean enabled, String cron, String zone, boolean runMakaniOnStartupIfEmpty) {
}
