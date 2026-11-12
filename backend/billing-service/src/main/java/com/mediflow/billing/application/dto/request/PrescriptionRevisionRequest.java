package com.mediflow.billing.application.dto.request;

import com.mediflow.billing.application.dto.command.PrescriptionRevisionCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PrescriptionRevisionRequest(
        @PositiveOrZero long expectedRevision,
        @Positive long revision,
        @NotNull UUID patientId,
        UUID recordId,
        @NotNull UUID departmentId,
        @NotBlank @Size(max = 32) String careEpisodeType,
        @NotNull UUID careEpisodeId,
        @NotBlank @Size(max = 64) String priceCode,
        @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal totalAmount,
        @NotEmpty @Size(max = 1000) List<@Valid Item> items) {

    public PrescriptionRevisionCommand toCommand(UUID prescriptionId, UUID operationId) {
        return new PrescriptionRevisionCommand(prescriptionId, operationId, null, expectedRevision, revision,
                patientId, recordId, departmentId, careEpisodeType, careEpisodeId, priceCode, totalAmount,
                items.stream().map(item -> new PrescriptionRevisionCommand.Item(item.drugId(), item.drugName(),
                        item.quantity(), item.unitPrice())).toList(), null);
    }

    public record Item(@NotNull UUID drugId, @NotBlank @Size(max = 255) String drugName,
            @Positive int quantity, @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal unitPrice) { }
}
