package com.elvo.identity.client;

import java.util.Optional;
import java.util.UUID;

public interface AccountReadClient {

    Optional<String> findEanByUserId(UUID userId);
}
