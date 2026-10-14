package com.mediflow.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

@SpringBootConfiguration
@EnableAutoConfiguration(exclude = org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration.class)
@ComponentScan(
        basePackages = {"com.mediflow.core", "com.mediflow.organization", "com.mediflow.patient"},
        excludeFilters = @ComponentScan.Filter(
                type = org.springframework.context.annotation.FilterType.REGEX,
                pattern = {
                        "com\\.mediflow\\.(organization|patient)\\.infrastructure\\.config\\.(SecurityConfig|RabbitMqConfig|RabbitConfig)"
                }))
public class CoreServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoreServiceApplication.class, args);
    }
}
