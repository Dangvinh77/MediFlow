package com.mediflow.clinical.application.dto.request;

import java.util.UUID;

public record RequestHandoverRequest(UUID toDoctorId, String reason, String pendingItems) {
}
