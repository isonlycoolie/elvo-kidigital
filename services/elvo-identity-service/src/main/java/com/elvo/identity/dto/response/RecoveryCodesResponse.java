package com.elvo.identity.dto.response;

import java.util.List;

public record RecoveryCodesResponse(
    List<String> codes,
    int remainingCount
) {
}