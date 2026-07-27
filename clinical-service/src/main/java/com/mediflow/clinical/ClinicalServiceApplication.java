package com.mediflow.clinical;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class ClinicalServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClinicalServiceApplication.class, args);
    }
}
