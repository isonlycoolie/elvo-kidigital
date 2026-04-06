package com.elvo.wallet.messaging.outbox;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class WalletOutboxService {

    public enum Status {
        PENDING,
        PUBLISHED,
        DEAD_LETTER
    }

    public record OutboxEvent(
            UUID eventId,
            String eventType,
            String routingKey,
            Map<String, Object> payload,
            String requestId,
            String correlationId,
            Instant createdAt,
            Instant publishedAt,
            Status status,
            int attemptCount,
            String lastError,
            Instant nextAttemptAt) {
    }

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public WalletOutboxService(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public UUID enqueue(String eventType,
                        String routingKey,
                        Map<String, Object> payload,
                        String requestId,
                        String correlationId,
                        Instant occurredAt) {
        UUID eventId = UUID.randomUUID();
        Instant createdAt = occurredAt == null ? Instant.now() : occurredAt;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("event_id", eventId)
                .addValue("event_type", eventType)
                .addValue("routing_key", routingKey)
                .addValue("payload_json", toJson(payload))
                .addValue("request_id", requestId)
                .addValue("correlation_id", correlationId)
                .addValue("created_at", Timestamp.from(createdAt))
                .addValue("status", Status.PENDING.name())
                .addValue("attempt_count", 0)
                .addValue("next_attempt_at", Timestamp.from(Instant.now()));

        jdbcTemplate.update("""
                INSERT INTO wallet_event_outbox (
                    event_id,
                    event_type,
                    routing_key,
                    payload_json,
                    request_id,
                    correlation_id,
                    created_at,
                    status,
                    attempt_count,
                    next_attempt_at
                ) VALUES (
                    :event_id,
                    :event_type,
                    :routing_key,
                    CAST(:payload_json AS jsonb),
                    :request_id,
                    :correlation_id,
                    :created_at,
                    :status,
                    :attempt_count,
                    :next_attempt_at
                )
                ON CONFLICT (event_id) DO NOTHING
                """, params);

        return eventId;
    }

    @Transactional
    public Optional<OutboxEvent> lockForDispatch(UUID eventId) {
        List<OutboxEvent> results = jdbcTemplate.query("""
                SELECT event_id,
                       event_type,
                       routing_key,
                       payload_json,
                       request_id,
                       correlation_id,
                       created_at,
                       published_at,
                       status,
                       attempt_count,
                       last_error,
                       next_attempt_at
                FROM wallet_event_outbox
                WHERE event_id = :event_id
                  AND status = 'PENDING'
                  AND next_attempt_at <= NOW()
                FOR UPDATE SKIP LOCKED
                """, new MapSqlParameterSource("event_id", eventId), mapper());
        return results.stream().findFirst();
    }

    @Transactional
    public List<OutboxEvent> lockBatchForReplay(Status status, int limit, String routingKeyPrefix) {
        int batchSize = Math.max(1, Math.min(limit, 200));
        String prefix = routingKeyPrefix == null ? "" : routingKeyPrefix.trim();
        return jdbcTemplate.query("""
                SELECT event_id,
                       event_type,
                       routing_key,
                       payload_json,
                       request_id,
                       correlation_id,
                       created_at,
                       published_at,
                       status,
                       attempt_count,
                       last_error,
                       next_attempt_at
                FROM wallet_event_outbox
                WHERE status = :status
                                    AND (:routing_key_prefix = '' OR routing_key LIKE CONCAT(:routing_key_prefix, '%'))
                ORDER BY created_at ASC
                LIMIT :limit
                FOR UPDATE SKIP LOCKED
                """, new MapSqlParameterSource()
                .addValue("status", status.name())
                                .addValue("routing_key_prefix", prefix)
                .addValue("limit", batchSize), mapper());
    }

    @Transactional
    public void markPublished(UUID eventId, Instant publishedAt) {
        jdbcTemplate.update("""
                UPDATE wallet_event_outbox
                SET status = 'PUBLISHED',
                    published_at = :published_at,
                    last_error = NULL
                WHERE event_id = :event_id
                """, new MapSqlParameterSource()
                .addValue("event_id", eventId)
                .addValue("published_at", Timestamp.from(publishedAt == null ? Instant.now() : publishedAt)));
    }

    @Transactional
    public void markDispatchFailure(UUID eventId,
                                    int nextAttemptCount,
                                    int maxAttempts,
                                    Duration baseBackoff,
                                    String errorMessage) {
        boolean deadLetter = nextAttemptCount >= maxAttempts;
        Instant nextAttemptAt = Instant.now().plus(computeBackoff(baseBackoff, Math.max(1, nextAttemptCount)));

        jdbcTemplate.update("""
                UPDATE wallet_event_outbox
                SET status = :status,
                    attempt_count = :attempt_count,
                    last_error = :last_error,
                    next_attempt_at = :next_attempt_at
                WHERE event_id = :event_id
                """, new MapSqlParameterSource()
                .addValue("event_id", eventId)
                .addValue("status", deadLetter ? Status.DEAD_LETTER.name() : Status.PENDING.name())
                .addValue("attempt_count", nextAttemptCount)
                .addValue("last_error", truncate(errorMessage, 512))
                .addValue("next_attempt_at", Timestamp.from(nextAttemptAt)));
    }

    private Duration computeBackoff(Duration baseBackoff, int attemptCount) {
        Duration normalized = baseBackoff == null || baseBackoff.isNegative() || baseBackoff.isZero()
                ? Duration.ofSeconds(2)
                : baseBackoff;
        long multiplier = 1L << Math.min(attemptCount - 1, 6);
        return normalized.multipliedBy(multiplier);
    }

    private RowMapper<OutboxEvent> mapper() {
        return (rs, rowNum) -> new OutboxEvent(
                rs.getObject("event_id", UUID.class),
                rs.getString("event_type"),
                rs.getString("routing_key"),
                fromJson(rs.getString("payload_json")),
                rs.getString("request_id"),
                rs.getString("correlation_id"),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("published_at")),
                Status.valueOf(rs.getString("status")),
                rs.getInt("attempt_count"),
                rs.getString("last_error"),
                toInstant(rs.getTimestamp("next_attempt_at")));
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize wallet outbox payload", ex);
        }
    }

    private Map<String, Object> fromJson(String value) {
        try {
            return objectMapper.readValue(value, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to deserialize wallet outbox payload", ex);
        }
    }

    private Instant toInstant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
