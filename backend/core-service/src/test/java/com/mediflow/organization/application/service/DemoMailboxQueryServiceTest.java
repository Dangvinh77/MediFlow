package com.mediflow.organization.application.service;

import com.mediflow.common.api.PageQuery;
import com.mediflow.organization.application.port.out.PatientAccountIdentityPort;
import com.mediflow.organization.application.port.out.SecurityMailStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class DemoMailboxQueryServiceTest {
    private final SecurityMailStore store = mock(SecurityMailStore.class);
    private final PatientAccountIdentityPort patients = mock(PatientAccountIdentityPort.class);

    @Test
    void disabledMailboxDoesNotQueryAccountsOrExposeMessageDetails() {
        var service = new DemoMailboxQueryService(store, patients, false);
        assertThat(service.config().enabled()).isFalse();
        assertThat(service.accounts(null, null, PageQuery.of(0, 10)).totalElements()).isZero();
        assertThat(service.messages(UUID.randomUUID(), PageQuery.of(0, 10)).totalElements()).isZero();
        assertThatThrownBy(() -> service.message(UUID.randomUUID(), UUID.randomUUID()))
                .hasMessageContaining("disabled");
        verifyNoInteractions(store, patients);
    }

    @Test
    void accountSearchAppliesRoleAndKeywordBeforeServerPageWindow() {
        UUID first = UUID.randomUUID(), second = UUID.randomUUID(), third = UUID.randomUUID();
        when(store.demoAccounts()).thenReturn(List.of(
                new SecurityMailStore.DemoAccount(first, null, "PATIENT", "Alice Nguyen", "alice@example.test"),
                new SecurityMailStore.DemoAccount(second, null, "PATIENT", "Alex Nguyen", "alex@example.test"),
                new SecurityMailStore.DemoAccount(third, null, "DOCTOR", "Alice Doctor", "doctor@example.test")));
        var service = new DemoMailboxQueryService(store, patients, true);

        var page = service.accounts("PATIENT", "example.test", PageQuery.of(1, 1));

        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.totalPages()).isEqualTo(2);
        assertThat(page.content()).hasSize(1);
        assertThat(page.content().getFirst().accountId()).isEqualTo(second);
    }

    @Test
    void messagesKeepPageMetadataAndMapMailboxHeaders() {
        UUID accountId = UUID.randomUUID(), messageId = UUID.randomUUID();
        var message = new SecurityMailStore.DemoMessage(messageId, "Reset password", "MediFlow",
                "demo@example.test", Instant.parse("2026-11-04T08:00:00Z"), "Preview", "Body", "<p>Body</p>");
        when(store.demoMessages(eq(accountId), any())).thenReturn(
                com.mediflow.common.api.PageResult.of(List.of(message), 5, 2, 2));

        var page = new DemoMailboxQueryService(store, patients, true)
                .messages(accountId, PageQuery.of(2, 2));

        assertThat(page.totalElements()).isEqualTo(5);
        assertThat(page.number()).isEqualTo(2);
        assertThat(page.size()).isEqualTo(2);
        assertThat(page.content().getFirst().from()).isEqualTo("MediFlow");
        assertThat(page.content().getFirst().to()).isEqualTo("demo@example.test");
    }
}
