package com.mediflow.billing.infrastructure.messaging.consumer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.billing.application.port.in.IssuePrescriptionChargeUseCase;
import com.mediflow.billing.application.port.in.ConsumePrescriptionChargeFactUseCase;
import com.mediflow.billing.application.port.out.PrescriptionChargeWirePort;
import com.mediflow.common.exception.BusinessRuleException;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.dao.DuplicateKeyException;

/** Called by the existing single dispatcher. Never falls back after a versioned-contract failure. */
public final class PrescriptionChargeConsumer implements ConsumePrescriptionChargeFactUseCase {
    private final ObjectMapper mapper;
    private final PrescriptionChargeWirePort wire;
    private final IssuePrescriptionChargeUseCase charges;
    private final com.mediflow.billing.application.port.in.ApplyPrescriptionTerminalUseCase terminal;
    public PrescriptionChargeConsumer(ObjectMapper mapper, PrescriptionChargeWirePort wire, IssuePrescriptionChargeUseCase charges) {
        this(mapper, wire, charges, null);
    }
    public PrescriptionChargeConsumer(ObjectMapper mapper, PrescriptionChargeWirePort wire, IssuePrescriptionChargeUseCase charges,
            com.mediflow.billing.application.port.in.ApplyPrescriptionTerminalUseCase terminal) {
        this.mapper = mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.wire = wire; this.charges = charges; this.terminal = terminal;
    }
    @Override public boolean receiveIfVersioned(String routingKey, byte[] body) {
        try {
            if (body == null || body.length == 0 || body.length > 1_048_576) throw new IllegalArgumentException();
            var root = mapper.readTree(body);
            if (root == null || !root.isObject()) throw new IllegalArgumentException();
            if (!root.has("payload") && !root.has("version") && !root.has("producer")) return false;
            if ("prescription.created".equals(routingKey)) charges.issue(wire.decode(routingKey, body));
            else {
                if (terminal == null) throw new IllegalArgumentException();
                terminal.apply(wire.decodeTerminal(routingKey, body));
            }
            return true;
        } catch (java.io.IOException | IllegalArgumentException | BusinessRuleException | DuplicateKeyException invalid) {
            throw new AmqpRejectAndDontRequeueException("Invalid, conflicting or unsupported Pharmacy V1 charge fact");
        }
    }
}
