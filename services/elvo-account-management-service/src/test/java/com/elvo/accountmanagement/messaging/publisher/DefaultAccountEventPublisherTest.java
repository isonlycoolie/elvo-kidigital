package com.elvo.accountmanagement.messaging.publisher;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.elvo.accountmanagement.config.AccountEventMessagingProperties;
import com.elvo.accountmanagement.entity.Account;
import com.elvo.accountmanagement.entity.Account.AccountStatus;
import com.elvo.accountmanagement.entity.Account.KycStatus;
import com.elvo.accountmanagement.messaging.event.AccountLifecyclePolicyEvent;

@ExtendWith(MockitoExtension.class)
class DefaultAccountEventPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private DefaultAccountEventPublisher publisher;
    private AccountEventMessagingProperties properties;

    @BeforeEach
    void setUp() {
        properties = new AccountEventMessagingProperties();
        properties.setExchange("elvo.account.exchange");
        properties.setLifecycleRoutingKey("account.lifecycle.v1");
        properties.setPolicyRoutingKey("account.policy.v1");
        publisher = new DefaultAccountEventPublisher(rabbitTemplate, properties);
    }

    @Test
    void publishLifecycleShouldUseLifecycleRoutingKey() {
        Account account = createAccount(UUID.randomUUID());

        publisher.publishLifecycle(
                account,
                "ACCOUNT_ACTIVATED",
                "manual",
                "req-1",
                "corr-1",
                "account-service",
                "127.0.0.1",
                "junit",
                null);

        verify(rabbitTemplate).convertAndSend(eq("elvo.account.exchange"), eq("account.lifecycle.v1"), any(AccountLifecyclePolicyEvent.class));
    }

    @Test
    void publishPolicyShouldUsePolicyRoutingKey() {
        Account account = createAccount(UUID.randomUUID());

        publisher.publishPolicy(
                account,
                "ACCOUNT_RESTRICTED",
                "fraud",
                "req-2",
                "corr-2",
                "account-service",
                "127.0.0.1",
                "junit",
                "system");

        verify(rabbitTemplate).convertAndSend(eq("elvo.account.exchange"), eq("account.policy.v1"), any(AccountLifecyclePolicyEvent.class));
    }

    private static Account createAccount(UUID accountId) {
        Account account = new Account();
        account.setAccountStatus(AccountStatus.ACTIVE);
        account.setKycStatus(KycStatus.VERIFIED);
        try {
            var field = Account.class.getDeclaredField("accountId");
            field.setAccessible(true);
            field.set(account, accountId);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
        return account;
    }
}
