package com.mediflow.report.application.dto.response;

import java.util.UUID;

/** HTTP representation of one grouped top-medicine result. */
public record TopMedicineDTO(UUID drugId, String drugName, int totalQuantity) {}
