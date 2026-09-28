package com.mediflow.surgery.infrastructure.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class JwtPropertiesTest {

    @Test
    void validHs256Secret_isAccepted() {
        assertThatCode(() -> new JwtProperties("surgery-test-secret-at-least-32-bytes"))
                .doesNotThrowAnyException();
    }

    @Test
    void missingSecret_isRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new JwtProperties(" "));
    }

    @Test
    void shortSecret_isRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new JwtProperties("too-short"));
    }

    @Test
    void unresolvedPlaceholder_isRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new JwtProperties("${MEDIFLOW_JWT_SECRET}"));
    }
}
