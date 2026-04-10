package com.elvo.identity.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.elvo.identity.client.AccountReadClient;

@Service
public class IdentityAccountReadService {

    private final AccountReadClient accountReadClient;

    public IdentityAccountReadService(AccountReadClient accountReadClient) {
        this.accountReadClient = accountReadClient;
    }

    public String resolveEan(UUID userId) {
        return accountReadClient.findEanByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("Account EAN not found for user"));
    }
}
