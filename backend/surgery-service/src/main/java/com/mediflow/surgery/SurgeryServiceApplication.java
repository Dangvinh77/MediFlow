package com.mediflow.surgery;

import com.mediflow.surgery.infrastructure.config.SurgeryFeatureProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableConfigurationProperties(SurgeryFeatureProperties.class)
public class SurgeryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SurgeryServiceApplication.class, args);
    }
}
