package com.mediflow.lab.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mediflow.lab.application.dto.request.AddResultRequest;
import com.mediflow.lab.application.dto.request.ChangeStatusRequest;
import com.mediflow.lab.application.dto.request.CreateLabRequest;
import com.mediflow.lab.application.dto.request.LabResultItem;
import com.mediflow.lab.domain.model.LabTestStatus;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

class LabDtoValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void addResultRequest_nullItem_reportsFieldViolationInsteadOfConstructorFailure() {
        AddResultRequest request = new AddResultRequest(
                java.util.Arrays.asList((LabResultItem) null), null, LocalDate.now());
        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("results[0].<list element>");
    }

    @Test
    void createLabRequest_missingAndFutureFields_areRejected() {
        CreateLabRequest request = new CreateLabRequest(
                null, null, null, " ", LocalDate.now().plusDays(1));

        Set<ConstraintViolation<CreateLabRequest>> violations = validator.validate(request);

        assertThat(violations).extracting(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .contains("recordId", "patientId", "requestingDepartmentId", "labType", "requestedDate");
    }

    @Test
    void addResultRequest_nestedItems_areValidated() {
        AddResultRequest request = new AddResultRequest(
                List.of(new LabResultItem(" ", " ", "unit", "range")),
                "conclusion",
                LocalDate.of(2026, 9, 1));

        Set<ConstraintViolation<AddResultRequest>> violations = validator.validate(request);

        assertThat(violations).extracting(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .contains("results[0].indicator", "results[0].value");
    }

    @Test
    void validRequests_haveNoViolations() {
        UUID recordId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();

        CreateLabRequest create = new CreateLabRequest(
                recordId, patientId, departmentId, "CBC", LocalDate.now());
        AddResultRequest results = new AddResultRequest(
                List.of(new LabResultItem("glucose", "<0.01", "mmol/L", "3.9-5.6")),
                null,
                LocalDate.now());

        assertThat(validator.validate(create)).isEmpty();
        assertThat(validator.validate(results)).isEmpty();
        assertThat(validator.validate(new ChangeStatusRequest(LabTestStatus.IN_PROGRESS))).isEmpty();
    }

    @Test
    void changeStatusRequest_missingStatus_isRejected() {
        assertThat(validator.validate(new ChangeStatusRequest(null)))
                .extracting(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .containsExactly("status");
    }
}
