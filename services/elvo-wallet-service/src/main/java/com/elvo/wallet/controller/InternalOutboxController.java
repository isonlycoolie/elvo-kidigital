package com.elvo.wallet.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.elvo.wallet.messaging.outbox.WalletOutboxDispatcher;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/v1/internal/outbox")
@Validated
@PreAuthorize("hasRole('INTERNAL_SERVICE')")
public class InternalOutboxController {

    private final WalletOutboxDispatcher outboxDispatcher;

    public InternalOutboxController(WalletOutboxDispatcher outboxDispatcher) {
        this.outboxDispatcher = outboxDispatcher;
    }

    @PostMapping("/replay-dead-letter")
    public ResponseEntity<Map<String, Object>> replayDeadLetter(
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int batchSize,
            @RequestParam(defaultValue = "wallet.") String routingKeyPrefix) {
        int replayed = outboxDispatcher.replayDeadLetter(batchSize, routingKeyPrefix);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("action", "wallet.outbox.replay_dead_letter");
        response.put("batchSize", batchSize);
        response.put("routingKeyPrefix", routingKeyPrefix);
        response.put("replayed", replayed);
        return ResponseEntity.accepted().body(response);
    }
}
