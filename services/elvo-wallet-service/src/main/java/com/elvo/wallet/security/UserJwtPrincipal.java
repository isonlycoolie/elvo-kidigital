package com.elvo.wallet.security;

import java.util.List;
import java.util.UUID;

public record UserJwtPrincipal(UUID userId, String ean, List<String> scopes) {
}
