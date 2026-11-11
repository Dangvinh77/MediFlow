package com.mediflow.notification.infrastructure.messaging;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.notification.application.port.out.StaffCriticalNoticePort;
import com.mediflow.notification.domain.model.StaffCriticalNotice;

/** Validates {@code lab.critical.raised} v1 and records one department notice per Lab notice id. */
public class LabCriticalNoticeDecoder {
    private static final Set<String> CRITICAL = Set.of("CRITICAL_LOW", "CRITICAL_HIGH");
    private final ObjectMapper mapper;
    private final StaffCriticalNoticePort notices;

    public LabCriticalNoticeDecoder(ObjectMapper mapper, StaffCriticalNoticePort notices) {
        this.mapper = mapper;
        this.notices = notices;
    }

    public void receive(byte[] body) {
        StaffCriticalNotice notice;
        try {
            JsonNode root = mapper.readTree(body);
            JsonNode payload = root.path("payload");
            if (!"lab.critical.raised".equals(text(root, "eventType")) || root.path("version").asInt() != 1
                    || !"lab-service".equals(text(root, "producer"))) {
                throw new IllegalArgumentException("Not a canonical lab.critical.raised v1 event");
            }
            String flag = text(payload, "flag");
            if (!CRITICAL.contains(flag)) {
                throw new IllegalArgumentException("Only critical values are notified");
            }
            if (!"OUTPATIENT_VISIT".equals(text(payload, "careEpisodeType"))) {
                throw new IllegalArgumentException("Deferred care episode");
            }
            JsonNode version = payload.path("resultVersion");
            if (!version.isIntegralNumber() || version.intValue() < 1) {
                throw new IllegalArgumentException("Invalid result version");
            }
            Instant.parse(text(payload, "releasedAt"));
            String value = payload.path("value").isTextual() ? payload.get("value").asText() : "—";
            String unit = payload.path("unit").isTextual() ? " " + payload.get("unit").asText() : "";
            String content = "Xét nghiệm " + text(payload, "labType") + ": " + text(payload, "label") + " = " + value
                    + unit + " (" + (flag.equals("CRITICAL_HIGH") ? "cao nguy cấp" : "thấp nguy cấp")
                    + "). Bác sĩ của khoa cần xác nhận đã nhận kết quả.";
            notice = new StaffCriticalNotice(uuid(payload, "noticeId"), uuid(root, "eventId"), uuid(payload, "labId"),
                    uuid(payload, "departmentId"), uuid(payload, "patientId"), version.intValue(),
                    "Giá trị xét nghiệm nguy cấp", content);
        } catch (Exception malformed) {
            throw new AmqpRejectAndDontRequeueException("Malformed lab.critical.raised", malformed);
        }
        notices.record(notice);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.asText();
    }

    private static UUID uuid(JsonNode node, String field) {
        return UUID.fromString(text(node, field));
    }
}
