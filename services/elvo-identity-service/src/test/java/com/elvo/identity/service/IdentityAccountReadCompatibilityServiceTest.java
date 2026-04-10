package com.elvo.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.elvo.identity.client.AccountReadClient;

@ExtendWith(MockitoExtension.class)
class IdentityAccountReadServiceTest {

    @Mock
    private AccountReadClient accountReadClient;

    private IdentityAccountReadService service;

    @BeforeEach
    void setUp() {
        service = new IdentityAccountReadService(accountReadClient);
    }

    @Test
    void shouldUseAccountServiceEanWhenPresent() {
        UUID userId = UUID.randomUUID();
        when(accountReadClient.findEanByUserId(userId)).thenReturn(Optional.of("EAN-REMOTE-123"));

        String resolved = service.resolveEan(userId);

        assertThat(resolved).isEqualTo("EAN-REMOTE-123");
    }

    @Test
    void shouldFailWhenRemoteEanMissing() {
        UUID userId = UUID.randomUUID();
        when(accountReadClient.findEanByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveEan(userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Account EAN not found for user");
    }
}
