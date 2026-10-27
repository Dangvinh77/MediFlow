package com.mediflow.notification.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.mediflow.notification.application.dto.command.PatientResultNoticeCommand;
import com.mediflow.notification.application.service.PatientResultNoticeService;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.stereotype.Component;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

/** Exact released producer envelopes; no contact lookup or patient login dependency. */
@Component
public class PatientResultNoticeDecoder {
    public static final Set<String> TYPES = Set.of("medicalrecord.completed", "lab.result.created", "followup.recommendation.created");
    private final ObjectMapper mapper;
    private final PatientResultNoticeService service;
    public PatientResultNoticeDecoder(ObjectMapper mapper, PatientResultNoticeService service) {
        this.mapper = mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.service = service;
    }
    public void receive(String type, byte[] body) {
        PatientResultNoticeCommand command;
        try {
            if (body == null || body.length == 0 || body.length > 1_048_576) throw new IllegalArgumentException("Invalid body size");
            JsonNode root = mapper.readTree(body);
            if (!TYPES.contains(type) || !type.equals(text(root, "eventType"))
                    || !root.path("version").isIntegralNumber() || !root.path("version").canConvertToInt()
                    || root.path("version").intValue() != 1
                    || !(type.equals("lab.result.created") ? "lab-service" : "clinical-service").equals(text(root, "producer")))
                throw new IllegalArgumentException("Unsupported result notice envelope");
            Instant.parse(text(root, "occurredAt"));
            var payload = root.path("payload");
            UUID source; String title; String content;
            switch (type) {
                case "lab.result.created" -> {
                    if (!"OUTPATIENT_VISIT".equals(text(payload, "careEpisodeType"))) throw new IllegalArgumentException("Deferred episode");
                    source = uuid(payload, "labId");
                    Instant.parse(text(payload, "completedAt")); LocalDate.parse(text(payload, "performedDate"));
                    title = "Kết quả xét nghiệm đã có";
                    content = "Kết quả xét nghiệm đã sẵn sàng. Bạn có thể xem trong mục Kết quả xét nghiệm.";
                }
                case "medicalrecord.completed" -> {
                    source = uuid(payload, "recordId"); Instant.parse(text(payload, "completedAt"));
                    title = "Kết quả khám đã có";
                    content = "Lượt khám đã hoàn tất. Bạn có thể xem kết luận và hướng dẫn trong mục Kết quả khám của tôi.";
                }
                default -> {
                    source = uuid(payload, "recommendationId");
                    var from = LocalDate.parse(text(payload, "recommendedFrom"));
                    var through = LocalDate.parse(text(payload, "recommendedThrough"));
                    if (through.isBefore(from)) throw new IllegalArgumentException("Invalid follow-up interval");
                    title = "Có đề xuất tái khám";
                    content = "Bác sĩ đã có đề xuất tái khám. Xem hướng dẫn trong mục Kết quả khám của tôi và liên hệ nurse để đăng ký.";
                }
            }
            command = new PatientResultNoticeCommand(uuid(root, "eventId"), type, source,
                    uuid(payload, "patientId"), text(root, "correlationId"),
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body)), title, content);
        } catch (Exception malformed) {
            throw new AmqpRejectAndDontRequeueException("Invalid patient result notice", malformed);
        }
        try { service.receive(command); }
        catch (com.mediflow.notification.domain.exception.NotificationEventConflictException conflict) {
            throw new AmqpRejectAndDontRequeueException("Conflicting patient result notice", conflict);
        }
    }
    private static String text(JsonNode root, String key) {
        var value = root.get(key);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) throw new IllegalArgumentException(key + " required");
        return value.textValue();
    }
    private static UUID uuid(JsonNode root, String key) {
        var value = text(root, key); var uuid = UUID.fromString(value);
        if (!uuid.toString().equals(value)) throw new IllegalArgumentException("Invalid UUID");
        return uuid;
    }
}
