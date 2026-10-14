package com.mediflow.patient.domain.model;

import com.mediflow.patient.domain.exception.InvalidPatientDataException;
import org.junit.jupiter.api.Test;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PatientBusinessRulesTest {
    @Test
    void createFutureBirthDateThrowsDomainRule() {
        assertCode(() -> valid().withDate(LocalDate.now().plusDays(1)).build(), "PATIENT_NGAYSINH_FUTURE");
    }

    @Test
    void createInvalidEmailThrowsDomainRule() {
        assertCode(() -> Patient.create("A", LocalDate.of(1990, 1, 1), Gender.M,
                "001", null, null, "bad", null), "PATIENT_EMAIL_INVALID");
    }

    @Test
    void createInvalidPhoneThrowsDomainRule() {
        assertCode(() -> Patient.create("A", LocalDate.of(1990, 1, 1), Gender.M,
                "001", null, "123", null, null), "PATIENT_SDT_INVALID");
    }

    @Test
    void createInvalidInsuranceThrowsDomainRule() {
        assertCode(() -> Patient.create("A", LocalDate.of(1990, 1, 1), Gender.M,
                "001", null, null, null, "123"), "PATIENT_BHYT_INVALID");
    }

    @Test
    void updateDoesNotChangeIdentityNumber() {
        Patient patient = Patient.create("A", LocalDate.of(1990, 1, 1), Gender.M,
                "001", null, null, null, null);
        patient.update("B", LocalDate.of(1991, 1, 1), Gender.F, "Hanoi", null, null, null);
        assertThat(patient.identityNumber()).isEqualTo("001");
        assertThat(patient.fullName()).isEqualTo("B");
    }

    private static void assertCode(ThrowingCallable action, String code) {
        assertThatThrownBy(action)
                .isInstanceOfSatisfying(InvalidPatientDataException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(code));
    }

    private static PatientBuilder valid() {
        return new PatientBuilder();
    }

    private static final class PatientBuilder {
        private LocalDate date = LocalDate.of(1990, 1, 1);

        private PatientBuilder withDate(LocalDate date) {
            this.date = date;
            return this;
        }

        private Patient build() {
            return Patient.create("A", date, Gender.M, "001", null, null, null, null);
        }
    }
}
