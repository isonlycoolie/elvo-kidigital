package com.elvo.accountmanagement.messaging.listener;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.elvo.accountmanagement.contract.AccountContracts.CreateAccountRequest;
import com.elvo.accountmanagement.entity.Account;
import com.elvo.accountmanagement.messaging.event.IdentityAccountCreationIntentEvent;
import com.elvo.accountmanagement.service.AccountManagementService;

@Component
public class IdentityRegistrationIntentListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(IdentityRegistrationIntentListener.class);

    private final AccountManagementService accountManagementService;

    public IdentityRegistrationIntentListener(AccountManagementService accountManagementService) {
        this.accountManagementService = accountManagementService;
    }

    @RabbitListener(queues = "${elvo.messaging.identity-registration.queue}")
    public void onIdentityRegistrationIntent(IdentityAccountCreationIntentEvent event) {
        if (event == null || event.userId() == null) {
            LOGGER.warn("Skipping identity registration intent event with missing user id");
            return;
        }

        CreateAccountRequest request = new CreateAccountRequest(
                event.userId(),
                event.ean(),
                Account.AccountType.WALLET,
                null,
                Account.KycStatus.UNVERIFIED,
                resolveRequestId(event),
                event.correlationId(),
                resolveSourceService(event),
                event.sourceIp(),
                event.sourceUserAgent());

        try {
            accountManagementService.createAccount(request);
            LOGGER.info("Created account from identity registration intent userId={} eventId={}", event.userId(), event.eventId());
        } catch (IllegalArgumentException ex) {
            if (isDuplicateAccount(ex)) {
                LOGGER.info("Ignoring duplicate account creation intent for userId={} eventId={} reason={}",
                        event.userId(), event.eventId(), ex.getMessage());
                return;
            }
            throw ex;
        }
    }

    private String resolveRequestId(IdentityAccountCreationIntentEvent event) {
        UUID eventId = event.eventId();
        return eventId == null ? null : eventId.toString();
    }

    private String resolveSourceService(IdentityAccountCreationIntentEvent event) {
        if (event.sourceService() != null && !event.sourceService().isBlank()) {
            return event.sourceService();
        }
        return "identity-service";
    }

    private boolean isDuplicateAccount(IllegalArgumentException ex) {
        String message = ex.getMessage();
        return message != null && message.contains("Account already exists for user");
    }
}
