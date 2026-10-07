package com.mediflow.surgery.application.dto.request;

import com.mediflow.surgery.application.dto.SurgeryLifecycleCommand;
import com.mediflow.surgery.application.port.in.CompleteSurgeryUseCase;
import com.mediflow.surgery.domain.model.SurgeryPerformedItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CompleteSurgeryRequest(@NotNull @PositiveOrZero Long expectedCaseRevision,
        @NotNull @Positive Long expectedScheduleRevision,
        @NotBlank @Size(max=64) String procedureCode, @NotBlank @Size(max=64) String methodCode,
        @NotBlank @Size(max=64) String outcomeCode,
        @Pattern(regexp="[A-Za-z0-9._-]{1,64}") String complicationGroupCode,
        @NotNull Instant actualStartAt, @NotNull Instant actualEndAt,
        @NotNull List<@NotNull @Valid PerformedItem> performedItems) {
    public record PerformedItem(@NotNull UUID performedItemId, @NotBlank @Size(max=64) String itemCode,
            @NotBlank @Size(max=64) String priceCode, @NotNull @DecimalMin(value="0",inclusive=false) BigDecimal quantity) {
        @com.fasterxml.jackson.annotation.JsonAnySetter
        public void rejectUnknownField(String name, Object value) { throw new IllegalArgumentException("Unexpected performed item field"); }
    }
    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void rejectUnknownField(String name, Object value) { throw new IllegalArgumentException("Unexpected completion field"); }
    @AssertTrue(message="actualEndAt must be after actualStartAt")
    public boolean isIntervalValid() { return actualStartAt == null || actualEndAt == null || actualEndAt.isAfter(actualStartAt); }
    @AssertTrue(message="performedItemId must be unique")
    public boolean isItemIdentityUnique() {
        return performedItems == null || performedItems.stream().anyMatch(java.util.Objects::isNull)
                || performedItems.stream().map(PerformedItem::performedItemId).distinct().count() == performedItems.size();
    }
    public CompleteSurgeryUseCase.Command toCommand(SurgeryLifecycleCommand identity) {
        return new CompleteSurgeryUseCase.Command(identity,procedureCode,methodCode,outcomeCode,complicationGroupCode,
                actualStartAt,actualEndAt,performedItems.stream().map(item -> new SurgeryPerformedItem(
                        item.performedItemId(),item.itemCode(),item.priceCode(),item.quantity())).toList());
    }
}
