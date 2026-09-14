package com.mediflow.pharmacy;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PharmacyServiceApplication {

    /** Cung cấp business clock dùng chung cho múi giờ bệnh viện và có thể kiểm thử deterministically. */
    @Bean
    public Clock pharmacyClock(
            @Value("${mediflow.pharmacy.business-zone:Asia/Ho_Chi_Minh}") String businessZone) {
        return Clock.system(ZoneId.of(businessZone));
    }

    public static void main(String[] args) {
        SpringApplication.run(PharmacyServiceApplication.class, args);
    }
}
