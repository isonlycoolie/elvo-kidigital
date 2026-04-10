package com.elvo.accountmanagement.messaging.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.elvo.accountmanagement.contract.AccountContracts.CreateAccountRequest;
import com.elvo.accountmanagement.messaging.event.IdentityAccountCreationIntentEvent;
import com.elvo.accountmanagement.service.AccountManagementService;

@ExtendWith(MockitoExtension.class)
class IdentityRegistrationIntentListenerTest {

    @Mock
    private AccountManagementService accountManagementService;

    private IdentityRegistrationIntentListener listener;

    @BeforeEach
    void setUp() {
        listener = new IdentityRegistrationIntentListener(accountManagementService);
    }

    @Test
    void shouldCreateAccountFromIdentityRegistrationIntent() {
        IdentityAccountCreationIntentEvent event = new IdentityAccountCreationIntentEvent(
                UUID.randomUUID(),
                "v1",
                null,
                "corr-1",
                UUID.randomUUID(),
                "EAN-100",
                "user@elvo.com",
                "+250700000001",
                "ELVO User",
                true,
                "identity-service",
                "127.0.0.1",
                "JUnit");

        listener.onIdentityRegistrationIntent(event);

        verify(accountManagementService).createAccount(any(CreateAccountRequest.class));
    }

    @Test
    void shouldIgnoreDuplicateAccountIntent() {
        IdentityAccountCreationIntentEvent event = new IdentityAccountCreationIntentEvent(
                UUID.randomUUID(),
                "v1",
                null,
                "corr-2",
                UUID.randomUUID(),
                "EAN-101",
                "user2@elvo.com",
                "+250700000002",
                "ELVO User 2",
                false,
                "identity-service",
                "127.0.0.1",
                "JUnit");

        when(accountManagementService.createAccount(any(CreateAccountRequest.class)))
                .thenThrow(new IllegalArgumentException("Account already exists for user"));

        listener.onIdentityRegistrationIntent(event);

        verify(accountManagementService).createAccount(any(CreateAccountRequest.class));
    }

    @Test
    void shouldSkipWhenUserIdIsMissing() {
        IdentityAccountCreationIntentEvent event = new IdentityAccountCreationIntentEvent(
                UUID.randomUUID(),
                "v1",
                null,
                "corr-3",
                null,
                "EAN-102",
                "user3@elvo.com",
                "+250700000003",
                "ELVO User 3",
                false,
                "identity-service",
                "127.0.0.1",
                "JUnit");

        listener.onIdentityRegistrationIntent(event);

        verify(accountManagementService, never()).createAccount(any(CreateAccountRequest.class));
    }
}
