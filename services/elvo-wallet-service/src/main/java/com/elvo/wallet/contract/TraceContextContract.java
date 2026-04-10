package com.elvo.wallet.contract;

import java.time.Instant;
import java.util.UUID;

/**
 * Shared trace context contract v1 for sensitive operations.
 */
public record TraceContextContract(
    String version,
    UUID correlationId,
    String requestId,
    String sourceService,
    UUID actorId,
    String actorType,
    String channel,
    String sourceIp,
    String sourceUserAgent,
    Instant occurredAt
) {

    public static final String V1 = "v1";

    public TraceContextContract {
        version = (version == null || version.isBlank()) ? V1 : version;
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
    }

    public static TraceContextContract v1(
        UUID correlationId,
        String requestId,
        String sourceService,
        UUID actorId,
        String actorType,
        String channel,
        String sourceIp,
        String sourceUserAgent,
        Instant occurredAt
    ) {
        return new TraceContextContract(
            V1,
            correlationId,
            requestId,
            sourceService,
            actorId,
            actorType,
            channel,
            sourceIp,
            sourceUserAgent,
            occurredAt
        );
    }
}