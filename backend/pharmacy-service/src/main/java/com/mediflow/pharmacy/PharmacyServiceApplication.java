package com.mediflow.pharmacy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

@SpringBootApplication
@EnableScheduling
public class PharmacyServiceApplication {

    /** Cung cấp UTC clock dùng chung để các job nghiệp vụ có thể kiểm thử deterministically. */
    @Bean
    public Clock pharmacyClock() {
        return Clock.systemUTC();
    }

    public static void main(String[] args) {
        SpringApplication.run(PharmacyServiceApplication.class, args);
    }
}
