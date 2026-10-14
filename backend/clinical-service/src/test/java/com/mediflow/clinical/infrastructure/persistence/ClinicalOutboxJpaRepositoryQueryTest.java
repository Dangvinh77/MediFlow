package com.mediflow.clinical.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import com.mediflow.clinical.infrastructure.persistence.repository.ClinicalOutboxJpaRepository;

class ClinicalOutboxJpaRepositoryQueryTest {

    @Test
    void outpatientSelectionExcludesAllDeferredAdmissionShapesBeforeApplyingTheBatchLimit() throws Exception {
        String query = normalize(query("lockUnpublishedForOutpatientMvp", int.class));

        assertThat(query).contains("event_type='admission.requested'")
                .contains("event_type='medicalrecord.completed'")
                .contains("in('admission','transfer')")
                .contains("payload->>'careepisodetype'")
                .contains("payload#>>'{payload,careepisodetype}'")
                .contains("coalesce(payload->>'disposition',payload#>>'{payload,disposition}','')")
                .contains("coalesce(payload->>'careepisodetype',payload#>>'{payload,careepisodetype}','')");
        assertThat(query.indexOf("andnot")).isLessThan(query.indexOf("limit:limit"));
        assertThat(query).contains("held_atisnull").contains("quarantined_atisnull");
    }

    @Test
    void holdUpdateUsesTheSameDeferredPredicateAndNeverAcknowledgesOrDeletesRows() throws Exception {
        String select = normalize(query("lockUnpublishedForOutpatientMvp", int.class));
        String hold = normalize(query("holdDeferredPendingEvents", java.time.Instant.class, String.class));
        String predicate = normalize(ClinicalOutboxJpaRepository.DEFERRED_OUTPATIENT_FACT_PREDICATE);

        assertThat(select).contains("andnot" + predicate);
        assertThat(hold).contains("and" + predicate);
        assertThat(hold).contains("held_at=:heldat").contains("hold_reason=:reason")
                .contains("published_atisnull").doesNotContain("published_at=")
                .doesNotContain("deletefrom");
    }

    @Test
    void ordinaryDispatchAlsoExcludesHeldAndQuarantinedRows() throws Exception {
        String query = normalize(query("lockUnpublished", int.class));

        assertThat(query).contains("published_atisnull")
                .contains("held_atisnull")
                .contains("quarantined_atisnull")
                .contains("limit:limit");
    }

    private static String query(String method, Class<?>... parameters) throws Exception {
        Method queryMethod = ClinicalOutboxJpaRepository.class.getMethod(method, parameters);
        return queryMethod.getAnnotation(Query.class).value();
    }

    private static String normalize(String value) {
        return value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }
}
