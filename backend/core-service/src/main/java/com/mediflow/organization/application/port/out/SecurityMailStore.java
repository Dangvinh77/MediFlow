package com.mediflow.organization.application.port.out;

import com.mediflow.organization.application.dto.response.MailDeliveryDTO;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Organization-owned persistence boundary for encrypted credential and recovery messages. */
public interface SecurityMailStore {
    void lockOperation(String operationKey);
    MailDeliveryDTO enqueue(NewMessage message);
    Optional<MailDeliveryDTO> byOperationKey(String operationKey);
    Optional<MailDeliveryDTO> latestForAccount(UUID accountId);
    MailDeliveryDTO retry(UUID accountId, UUID messageId);
    /** Patient-scoped retry additionally requires the message's stored recipient to be current. */
    MailDeliveryDTO retry(UUID accountId, UUID messageId, String expectedRecipient);
    List<QueuedMessage> claimDue(int limit);
    void markSent(UUID messageId);
    void markFailed(UUID messageId, String failureCode);
    List<DemoAccount> demoAccounts();
    PageResult<DemoMessage> demoMessages(UUID accountId, PageQuery page);
    Optional<DemoMessage> demoMessage(UUID accountId, UUID messageId);
    int incrementRecoveryRequest(String emailHash, Instant windowStart);

    record NewMessage(UUID accountId, String from, String recipient, String kind, String operationKey,
            String subject, String textBody, String htmlBody) { }
    record MailContent(String from, String recipient, String subject, String textBody, String htmlBody) { }
    record QueuedMessage(UUID messageId, boolean demoMailbox, MailContent content) { }
    record DemoAccount(UUID accountId, UUID patientId, String role, String displayName, String email) { }
    record DemoMessage(UUID messageId, String subject, String from, String to, Instant createdAt,
            String preview, String textBody, String htmlBody) { }
}
