package com.mediflow.pharmacy.infrastructure.messaging;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

class OutpatientEventActivationTest {
    @Test void defaultOffDoesNotRequireOrInventAcceptance(){assertThat(new OutpatientEventActivation(new MockEnvironment()).enabled()).isFalse();}
    @ParameterizedTest @ValueSource(strings={"profile","reference","mediflow.features.care-finance-v2","mediflow.pharmacy.identity.enabled",
            "mediflow.pharmacy.clinical-context.enabled","mediflow.pharmacy.clearance-consumer.enabled",
            "mediflow.pharmacy.clearance-authority.enabled","mediflow.pharmacy.outpatient-api.enabled"})
    void refusesPartialOrUnacceptedActivation(String missing){var env=enabled();if(missing.equals("profile"))env.setActiveProfiles("default");
        else env.setProperty(missing.equals("reference")?"mediflow.pharmacy.outpatient-events.acceptance-reference":missing,missing.equals("reference")?"":"false");
        assertThatThrownBy(()->new OutpatientEventActivation(env)).isInstanceOf(IllegalStateException.class);}
    @Test void explicitLocalAcceptanceCanActivateNewCohorts(){var policy=new OutpatientEventActivation(enabled());assertThat(policy.enabled()).isTrue();assertThat(policy.reference()).isEqualTo("ISOLATED_TEST_ONLY");}
    static MockEnvironment enabled(){var env=new MockEnvironment();env.setActiveProfiles("outpatient-mvp");
        for(String flag:java.util.List.of("mediflow.features.care-finance-v2","mediflow.pharmacy.identity.enabled",
                "mediflow.pharmacy.clinical-context.enabled","mediflow.pharmacy.clearance-consumer.enabled",
                "mediflow.pharmacy.clearance-authority.enabled","mediflow.pharmacy.outpatient-api.enabled",
                "mediflow.pharmacy.outpatient-events.enabled"))env.setProperty(flag,"true");
        env.setProperty("mediflow.pharmacy.outpatient-events.acceptance-reference","ISOLATED_TEST_ONLY");return env;}
}
