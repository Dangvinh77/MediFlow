package com.mediflow.inpatient.application.dto.event;

import java.time.Instant;
import java.util.UUID;

public record DomainEventEnvelope<T>(
        UUID maSuKien,
        String loaiSuKien,
        int phienBan,
        Instant xayRaLuc,
        String maTuongQuan,
        String dichVuPhat,
        T duLieu) {
}
