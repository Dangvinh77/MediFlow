package com.mediflow.surgery;

import com.mediflow.surgery.infrastructure.config.SurgeryFeatureProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableFeignClients
@EnableConfigurationProperties(SurgeryFeatureProperties.class)
public class SurgeryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SurgeryServiceApplication.class, args);
    }
}
