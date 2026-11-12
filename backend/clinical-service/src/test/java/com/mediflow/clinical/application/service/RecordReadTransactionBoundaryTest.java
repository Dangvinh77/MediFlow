package com.mediflow.clinical.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.clinical.infrastructure.persistence.adapter.MedicalRecordPersistenceAdapter;

/** A read must not hold a database connection across the Core call and the separate log transaction. */
class RecordReadTransactionBoundaryTest {
    @Test
    void policyReadsRunOutsideAServiceTransaction() throws Exception {
        assertThat(MedicalRecordApplicationService.class.getMethod("getById", UUID.class)
                .isAnnotationPresent(Transactional.class)).isFalse();
        assertThat(MedicalRecordApplicationService.class.getMethod("byPatient", UUID.class)
                .isAnnotationPresent(Transactional.class)).isFalse();
        assertThat(RecordAccessApplicationService.class.getMethod("capabilities", UUID.class)
                .isAnnotationPresent(Transactional.class)).isFalse();
    }

    @Test
    void recordLoadsKeepTheirOwnShortTransaction() throws Exception {
        assertThat(MedicalRecordPersistenceAdapter.class.getMethod("findByPatient", UUID.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(MedicalRecordPersistenceAdapter.class.getMethod("findById", UUID.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
    }
}
