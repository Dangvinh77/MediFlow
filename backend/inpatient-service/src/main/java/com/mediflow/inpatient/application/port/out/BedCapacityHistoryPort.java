package com.mediflow.inpatient.application.port.out;

import com.mediflow.inpatient.domain.model.BedCapacitySnapshot;

public interface BedCapacityHistoryPort {
    void append(BedCapacitySnapshot snapshot);
}
