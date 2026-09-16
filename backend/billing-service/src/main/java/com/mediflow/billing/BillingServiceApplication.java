package com.mediflow.billing;

import java.time.Clock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BillingServiceApplication {

    /** Infrastructure clock shared by outbox scheduling and deterministic tests. */
    @Bean
    public Clock billingClock() {
        return Clock.systemUTC();
    }

    public static void main(String[] args) {
        SpringApplication.run(BillingServiceApplication.class, args);
    }
}
