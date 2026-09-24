package com.mediflow.inpatient.infrastructure.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class JwtPropertiesTest {

    @Test
    void constructor_rejectsMissingOrShortSecret() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new JwtProperties(null))
                .withMessageContaining("mediflow.jwt.secret");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new JwtProperties("   "))
                .withMessageContaining("mediflow.jwt.secret");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new JwtProperties("too-short"))
                .withMessageContaining("32 byte");
    }

    @Test
    void constructor_acceptsSecretWithAtLeast32Utf8Bytes() {
        String secret = "inpatient-test-secret-at-least-32-bytes";

        JwtProperties properties = new JwtProperties(secret);

        assertThat(properties.secret()).isEqualTo(secret);
    }
}
