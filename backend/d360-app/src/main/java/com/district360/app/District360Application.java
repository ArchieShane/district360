package com.district360.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.district360")
@ConfigurationPropertiesScan(basePackages = "com.district360.app")
@EnableScheduling
public class District360Application {

    public static void main(String[] args) {
        SpringApplication.run(District360Application.class, args);
    }
}
