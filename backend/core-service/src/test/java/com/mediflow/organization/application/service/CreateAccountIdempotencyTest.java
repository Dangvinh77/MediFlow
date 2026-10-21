package com.mediflow.organization.application.service;

import com.mediflow.common.exception.DuplicateResourceException;
import com.mediflow.organization.application.port.out.AccountCreationReceiptRepository;
import com.mediflow.organization.application.port.out.AccountCreationReceiptRepository.Receipt;
import com.mediflow.organization.application.port.out.AccountRepository;
import com.mediflow.organization.application.port.out.PasswordHasher;
import com.mediflow.organization.domain.model.Account;
import com.mediflow.organization.domain.model.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateAccountIdempotencyTest {
    @Mock AccountRepository accounts;
    @Mock PasswordHasher passwords;
    @Mock AccountCreationReceiptRepository receipts;

    @Test
    void replayUsesOriginalAccountAndRejectsChangedPasswordOrOwner() {
        UUID actor = UUID.randomUUID();
        UUID patient = UUID.randomUUID();
        when(passwords.hash("original-password")).thenReturn("salted-hash");
        when(accounts.save(any(Account.class))).thenAnswer(call -> call.getArgument(0));
        var service = new CreateAccountService(accounts, passwords, receipts);
        var first = service.executeWithIdempotency(actor, "key", "patient.demo",
                "original-password", null, patient, Role.PATIENT);
        var captor = org.mockito.ArgumentCaptor.forClass(Receipt.class);
        verify(receipts).save(captor.capture());
        Receipt receipt = captor.getValue();
        assertThat(receipt.passwordHash()).isEqualTo("salted-hash");
        assertThat(receipt.requestFingerprint()).hasSize(64).doesNotContain("original-password");
        when(receipts.find(actor, "key")).thenReturn(Optional.of(receipt));
        when(passwords.matches("original-password", "salted-hash")).thenReturn(true);
        when(accounts.findById(first.account().getAccountId())).thenReturn(Optional.of(first.account()));

        var replay = service.executeWithIdempotency(actor, "key", "patient.demo",
                "original-password", null, patient, Role.PATIENT);
        assertThat(first.replayed()).isFalse();
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.account().getAccountId()).isEqualTo(first.account().getAccountId());
        var passwordConflict = assertThrows(DuplicateResourceException.class,
                () -> service.executeWithIdempotency(actor, "key", "patient.demo",
                        "different-password", null, patient, Role.PATIENT));
        assertThat(passwordConflict.getCode()).isEqualTo("ACCOUNT_IDEMPOTENCY_KEY_REUSED");
        assertThrows(DuplicateResourceException.class,
                () -> service.executeWithIdempotency(actor, "key", "patient.demo",
                        "original-password", null, UUID.randomUUID(), Role.PATIENT));
        verify(accounts, times(1)).save(any(Account.class));
        verify(receipts, times(1)).save(any(Receipt.class));
    }
}
