package com.mediflow.billing.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.billing.application.port.in.IssuePrescriptionChargeUseCase;
import com.mediflow.billing.application.port.out.LedgerEventPort;
import com.mediflow.billing.application.port.out.PrescriptionChargeRepositoryPort;
import com.mediflow.billing.application.port.out.PrescriptionChargeWirePort;
import com.mediflow.billing.application.port.out.PrescriptionPricePolicyPort;
import com.mediflow.billing.application.service.PrescriptionChargeService;
import com.mediflow.billing.infrastructure.messaging.consumer.PrescriptionChargeConsumer;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = {"mediflow.billing.ledger.enabled", "mediflow.billing.prescription-charge-consumer.enabled"}, havingValue = "true")
public class PrescriptionChargeConfiguration {
    @Bean PrescriptionPricePolicyPort prescriptionPricePolicy(
            @org.springframework.beans.factory.annotation.Value("${mediflow.billing.prescription-charges.allowed-price-codes:}") String configuredCodes) {
        var codes = java.util.Arrays.stream(configuredCodes.split(",")).map(String::trim).filter(value -> !value.isEmpty())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (codes.isEmpty() || codes.stream().anyMatch(value -> value.length() > 64 || value.chars().anyMatch(Character::isISOControl)))
            throw new IllegalArgumentException("Explicit Billing prescription price-code policy is required");
        return code -> {
            if (!codes.contains(code)) throw new com.mediflow.billing.domain.exception.BillingRuleException("BILLING_PRESCRIPTION_PRICE_CODE_UNSUPPORTED", "BILLING_PRESCRIPTION_PRICE_CODE_UNSUPPORTED");
        };
    }
    @Bean IssuePrescriptionChargeUseCase issuePrescriptionCharges(PrescriptionChargeRepositoryPort repository, LedgerEventPort events, PlatformTransactionManager manager,
            PrescriptionPricePolicyPort prices, com.mediflow.billing.application.port.out.PrescriptionTerminalRepositoryPort terminal) {
        var service = new PrescriptionChargeService(repository, events, Clock.systemUTC(), prices);
        var transaction = new TransactionTemplate(manager); transaction.setTimeout(5);
        return command -> transaction.execute(status -> {
            var issued = service.issue(command);
            terminal.recoverDuringIssuance(command.prescriptionId());
            return issued;
        });
    }
    @Bean com.mediflow.billing.application.port.in.ApplyPrescriptionTerminalUseCase prescriptionTerminal(
            com.mediflow.billing.application.port.out.PrescriptionTerminalRepositoryPort repository, PlatformTransactionManager manager) {
        var service = new com.mediflow.billing.application.service.PrescriptionTerminalService(repository, Clock.systemUTC());
        var tx = new TransactionTemplate(manager); tx.setTimeout(5);
        return command -> tx.executeWithoutResult(status -> service.apply(command));
    }
    @Bean PrescriptionChargeConsumer prescriptionChargeConsumer(ObjectMapper mapper, PrescriptionChargeWirePort wire, IssuePrescriptionChargeUseCase charges,
            com.mediflow.billing.application.port.in.ApplyPrescriptionTerminalUseCase terminal) {
        return new PrescriptionChargeConsumer(mapper, wire, charges, terminal);
    }
    @Bean com.mediflow.billing.application.port.in.GetPrescriptionAdjustmentUseCase prescriptionAdjustments(
            com.mediflow.billing.application.port.out.PrescriptionTerminalRepositoryPort repository, PlatformTransactionManager manager) {
        var tx = new TransactionTemplate(manager); tx.setReadOnly(true); tx.setTimeout(5);
        return rx -> tx.execute(status -> repository.get(rx));
    }
}
