package com.mediflow.report.application.port.out;

import com.mediflow.report.application.dto.command.DispensedItem;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/** Durable source dedupe for the CURRENT single-fill prescription contract, not delivery dedupe. */
public interface PrescriptionFillReceiptPort {
    /** Joins the caller's projection transaction; identical source returns false, changed proof rejects. */
    boolean claim(UUID prescriptionId, Instant filledAt, UUID departmentId, ZoneId reportZone,
                  List<DispensedItem> groupedItems);
}
