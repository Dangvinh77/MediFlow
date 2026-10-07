package com.mediflow.surgery.application.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.mediflow.surgery.application.dto.SurgeryActorIdentity;
import com.mediflow.surgery.application.port.in.CreateSurgeryCaseUseCase;
import com.mediflow.surgery.domain.model.CareEpisode;
import com.mediflow.surgery.domain.model.CareEpisodeType;
import com.mediflow.surgery.domain.model.SurgeryAuditActor;
import com.mediflow.surgery.domain.model.SurgeryPlannedItem;
import com.mediflow.surgery.domain.model.SurgeryPriority;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Requester is a source reference, not permission to act or a substitute for the JWT recorder. */
public record CreateSurgeryRequest(
        @NotNull UUID surgeryRequestId,
        @NotNull CareEpisodeType careEpisodeType,
        @NotNull UUID careEpisodeId,
        UUID admissionId,
        UUID recordId,
        @NotNull UUID patientId,
        @NotNull UUID departmentId,
        @NotNull UUID requestedBy,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{1,64}") String procedureCode,
        @NotBlank @Size(max = 4000) String indication,
        @NotNull SurgeryPriority priority,
        @NotNull Instant requestedAt,
        @NotNull @Positive Long templateRevision,
        @NotNull @Size(min = 1, max = 100) List<@NotNull @Valid PlannedItem> plannedItems) {

    public record PlannedItem(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{1,64}") String itemCode,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{1,64}") String priceCode,
            @NotNull @DecimalMin(value = "0", inclusive = false)
            @Digits(integer = 15, fraction = 4) BigDecimal quantity) {
        @JsonAnySetter
        public void rejectUnknownField(String name, Object value) {
            throw new IllegalArgumentException("Unexpected planned item field");
        }
    }

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unexpected creation field");
    }

    @AssertTrue(message = "admissionId must match the selected admission episode and be absent for outpatient")
    public boolean isEpisodeConsistent() {
        if (careEpisodeType == null || careEpisodeId == null) return true;
        return careEpisodeType == CareEpisodeType.ADMISSION
                ? careEpisodeId.equals(admissionId) : admissionId == null;
    }

    @AssertTrue(message = "planned item codes must be unique")
    public boolean isItemIdentityUnique() {
        return plannedItems == null || plannedItems.stream().anyMatch(Objects::isNull)
                || plannedItems.stream().map(PlannedItem::itemCode).distinct().count() == plannedItems.size();
    }

    public CreateSurgeryCaseUseCase.Command toCommand(SurgeryActorIdentity identity, String correlationId) {
        return new CreateSurgeryCaseUseCase.Command(surgeryRequestId,
                new CareEpisode(careEpisodeType, careEpisodeId, admissionId, recordId),
                patientId, departmentId, requestedBy, procedureCode, indication, priority,
                requestedAt, templateRevision,
                plannedItems.stream().map(item -> new SurgeryPlannedItem(
                        item.itemCode(), item.priceCode(), item.quantity())).toList(),
                SurgeryAuditActor.human(identity.accountId(), identity.verifiedStaffId()), correlationId);
    }
}
