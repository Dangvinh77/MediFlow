package com.mediflow.notification.application.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyTextTest {
    @Test void groupsThousandsWithDotsAndDropsZeroFraction() {
        assertThat(MoneyText.of(new BigDecimal("36000"), "VND")).isEqualTo("36.000 VND");
        assertThat(MoneyText.of(new BigDecimal("150000.00"), "VND")).isEqualTo("150.000 VND");
        assertThat(MoneyText.of(new BigDecimal("1250500.50"), "VND")).isEqualTo("1.250.500,5 VND");
        assertThat(MoneyText.of(new BigDecimal("0.00"), null)).isEqualTo("0 VND");
        assertThat(MoneyText.amount(new BigDecimal("950"))).isEqualTo("950");
        assertThat(MoneyText.amount(null)).isEmpty();
    }
}
