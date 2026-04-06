package com.elvo.wallet.controller;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.elvo.wallet.dto.request.InternalReservationActionRequestDto;
import com.elvo.wallet.dto.request.ReservationRequestDto;
import com.elvo.wallet.dto.response.BalanceResponseDto;
import com.elvo.wallet.dto.response.FlowResultResponseDto;
import com.elvo.wallet.entity.Wallet;
import com.elvo.wallet.mapper.WalletMapper;
import com.elvo.wallet.repository.WalletRepository;
import com.elvo.wallet.security.WalletOperationRateLimitService;
import com.elvo.wallet.service.WalletService;
import com.elvo.wallet.service.model.ReservationCommand;
import com.elvo.wallet.service.model.WalletFlowResult;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/internal/wallets")
@Validated
@PreAuthorize("hasRole('INTERNAL_SERVICE')")
public class InternalWalletController {

    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("audit.wallet.internal.controller");

    private final WalletRepository walletRepository;
    private final WalletService walletService;
    private final WalletMapper walletMapper;
        private final WalletOperationRateLimitService operationRateLimitService;

        public InternalWalletController(WalletRepository walletRepository,
                                                                        WalletService walletService,
                                                                        WalletMapper walletMapper,
                                                                        WalletOperationRateLimitService operationRateLimitService) {
        this.walletRepository = walletRepository;
        this.walletService = walletService;
        this.walletMapper = walletMapper;
                this.operationRateLimitService = operationRateLimitService;
    }

    @GetMapping("/{userId}/balance")
    public ResponseEntity<BalanceResponseDto> getBalance(@PathVariable UUID userId) {
        Wallet wallet = walletByUser(userId);
        AUDIT_LOG.info("internal_wallet_balance_lookup userId={} walletId={}", userId, wallet.getId());
        return ResponseEntity.ok(walletMapper.toBalanceResponseDto(wallet));
    }

    @PostMapping("/{userId}/reserve")
    public ResponseEntity<FlowResultResponseDto> reserve(
            @PathVariable UUID userId,
            @Valid @RequestBody ReservationRequestDto request,
            HttpServletRequest httpRequest
    ) {
        Wallet wallet = walletByUser(userId);
        WalletOperationRateLimitService.RateLimitResult reserveRateLimit = operationRateLimitService.enforce(
                WalletOperationRateLimitService.Operation.RESERVE,
                userId,
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("X-Device-Id"),
                null);
        if (!reserveRateLimit.allowed()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new FlowResultResponseDto(false, "Reserve rate limit exceeded", wallet.getId(), null, "wallet.reservation.failed"));
        }

        ReservationCommand command = new ReservationCommand(
                wallet.getId(),
                userId,
                request.getAmount(),
                request.getExpiryDate(),
                request.getIdempotencyKey(),
                UUID.randomUUID().toString()
        );

        WalletFlowResult result = walletService.createReservation(command);
        AUDIT_LOG.info("internal_wallet_reserve userId={} walletId={} success={} message={}",
                userId, wallet.getId(), result.success(), result.message());
        return toResponse(result);
    }

    @PostMapping("/{userId}/release")
    public ResponseEntity<FlowResultResponseDto> release(
            @PathVariable UUID userId,
            @Valid @RequestBody InternalReservationActionRequestDto request
    ) {
        Wallet wallet = walletByUser(userId);
        WalletFlowResult result = walletService.releaseReservation(request.getReservationId(), request.getIdempotencyKey());
        AUDIT_LOG.info("internal_wallet_release userId={} walletId={} reservationId={} success={} message={}",
                userId, wallet.getId(), request.getReservationId(), result.success(), result.message());
        return toResponse(result);
    }

    @PostMapping("/{userId}/confirm-debit")
    public ResponseEntity<FlowResultResponseDto> confirmDebit(
            @PathVariable UUID userId,
            @Valid @RequestBody InternalReservationActionRequestDto request
    ) {
        Wallet wallet = walletByUser(userId);
        WalletFlowResult result = walletService.confirmReservation(request.getReservationId(), request.getIdempotencyKey());
        AUDIT_LOG.info("internal_wallet_confirm_debit userId={} walletId={} reservationId={} success={} message={}",
                userId, wallet.getId(), request.getReservationId(), result.success(), result.message());
        return toResponse(result);
    }

    @PostMapping("/{userId}/reverse")
    public ResponseEntity<FlowResultResponseDto> reverse(
            @PathVariable UUID userId,
            @Valid @RequestBody InternalReservationActionRequestDto request,
            HttpServletRequest httpRequest
    ) {
        Wallet wallet = walletByUser(userId);
        WalletOperationRateLimitService.RateLimitResult reverseRateLimit = operationRateLimitService.enforce(
                WalletOperationRateLimitService.Operation.REVERSE,
                userId,
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("X-Device-Id"),
                null);
        if (!reverseRateLimit.allowed()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new FlowResultResponseDto(false, "Reverse rate limit exceeded", wallet.getId(), null, "wallet.reservation.failed"));
        }

        WalletFlowResult result = walletService.releaseReservation(request.getReservationId(), request.getIdempotencyKey());
        AUDIT_LOG.info("internal_wallet_reverse userId={} walletId={} reservationId={} success={} message={}",
                userId, wallet.getId(), request.getReservationId(), result.success(), result.message());
        return toResponse(result);
    }

    private Wallet walletByUser(UUID userId) {
        return walletRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Wallet not found for user: " + userId));
    }

    private ResponseEntity<FlowResultResponseDto> toResponse(WalletFlowResult result) {
        FlowResultResponseDto response = walletMapper.toFlowResultResponseDto(result);
        return result.success()
                ? ResponseEntity.ok(response)
                : ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
}