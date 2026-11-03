package com.mediflow.organization.application.service;

import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.organization.application.dto.response.DemoMailboxAccountDTO;
import com.mediflow.organization.application.dto.response.DemoMailboxConfigDTO;
import com.mediflow.organization.application.dto.response.DemoMailboxMessageDTO;
import com.mediflow.organization.application.dto.response.DemoMailboxMessageDetailDTO;
import com.mediflow.organization.application.port.out.PatientAccountIdentityPort;
import com.mediflow.organization.application.port.out.SecurityMailStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional(value = "organizationTransactionManager", readOnly = true)
public class DemoMailboxQueryService {
    private final SecurityMailStore messages;
    private final PatientAccountIdentityPort patients;
    private final boolean enabled;

    public DemoMailboxQueryService(SecurityMailStore messages, PatientAccountIdentityPort patients,
            @Value("${mediflow.demo-mailbox.enabled:false}") boolean enabled) {
        this.messages = messages;
        this.patients = patients;
        this.enabled = enabled;
    }

    public DemoMailboxConfigDTO config() { return new DemoMailboxConfigDTO(enabled); }

    public PageResult<DemoMailboxAccountDTO> accounts(String role, String keyword, PageQuery page) {
        if (!enabled) return PageResult.empty(page);
        String term = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        List<DemoMailboxAccountDTO> matching = messages.demoAccounts().stream().map(account -> {
            String displayName = account.displayName();
            if ("PATIENT".equals(account.role())) {
                try { displayName = patients.displayName(account.patientId()); } catch (RuntimeException ignored) { }
            }
            if (displayName == null || displayName.isBlank()) displayName = account.email();
            return new DemoMailboxAccountDTO(account.accountId(), account.role(), displayName, account.email());
        }).filter(account -> role == null || account.role().equalsIgnoreCase(role))
                .filter(account -> term.isEmpty() || account.email().toLowerCase(Locale.ROOT).contains(term)
                        || account.displayName().toLowerCase(Locale.ROOT).contains(term))
                .toList();
        int start = Math.min(page.page() * page.size(), matching.size());
        int end = Math.min(start + page.size(), matching.size());
        return PageResult.of(matching.subList(start, end), matching.size(), page.page(), page.size());
    }

    public PageResult<DemoMailboxMessageDTO> messages(UUID accountId, PageQuery page) {
        if (!enabled) return PageResult.empty(page);
        return messages.demoMessages(accountId, page).map(message -> new DemoMailboxMessageDTO(
                message.messageId(), message.subject(), message.from(), message.to(), message.createdAt(), message.preview()));
    }

    public DemoMailboxMessageDetailDTO message(UUID accountId, UUID messageId) {
        if (!enabled) throw new com.mediflow.common.exception.ResourceNotFoundException(
                "DEMO_MAILBOX_DISABLED", "Demo mailbox is disabled");
        var message = messages.demoMessage(accountId, messageId)
                .orElseThrow(() -> new com.mediflow.common.exception.ResourceNotFoundException(
                        "DEMO_MAIL_NOT_FOUND", "Demo email was not found"));
        return new DemoMailboxMessageDetailDTO(message.messageId(), message.subject(), message.from(),
                message.to(), message.createdAt(), message.textBody(), message.htmlBody());
    }
}
