package com.elvo.wallet.controller;

import java.math.BigDecimal;
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

import com.elvo.wallet.dto.request.DelegatedWithdrawalTokenCancelRequestDto;
import com.elvo.wallet.dto.request.DelegatedWithdrawalTokenIssueRequestDto;
import com.elvo.wallet.dto.request.InternalReservationActionRequestDto;
import com.elvo.wallet.dto.request.MakerCheckerDecisionRequestDto;
import com.elvo.wallet.dto.request.ReservationRequestDto;
import com.elvo.wallet.dto.response.BalanceResponseDto;
import com.elvo.wallet.dto.response.DelegatedWithdrawalTokenResponseDto;
import com.elvo.wallet.dto.response.FlowResultResponseDto;
import com.elvo.wallet.dto.response.WalletResponseDto;
import com.elvo.wallet.entity.Wallet;
import com.elvo.wallet.mapper.WalletMapper;
import com.elvo.wallet.repository.WalletRepository;
import com.elvo.wallet.security.WalletOperationRateLimitService;
import com.elvo.wallet.security.MakerCheckerApprovalService;
import com.elvo.wallet.service.DelegatedWithdrawalTokenLifecycleService;
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
        private final MakerCheckerApprovalService makerCheckerApprovalService;
        private final DelegatedWithdrawalTokenLifecycleService delegatedWithdrawalTokenLifecycleService;

        public InternalWalletController(WalletRepository walletRepository,
                                                                        WalletService walletService,
                                                                        WalletMapper walletMapper,
                                                                        WalletOperationRateLimitService operationRateLimitService,
                                                                        MakerCheckerApprovalService makerCheckerApprovalService,
                                                                        DelegatedWithdrawalTokenLifecycleService delegatedWithdrawalTokenLifecycleService) {
        this.walletRepository = walletRepository;
        this.walletService = walletService;
        this.walletMapper = walletMapper;
                this.operationRateLimitService = operationRateLimitService;
                this.makerCheckerApprovalService = makerCheckerApprovalService;
                this.delegatedWithdrawalTokenLifecycleService = delegatedWithdrawalTokenLifecycleService;
    }

    @GetMapping("/{userId}/balance")
    public ResponseEntity<BalanceResponseDto> getBalance(@PathVariable UUID userId) {
        Wallet wallet = walletByUser(userId);
        AUDIT_LOG.info("internal_wallet_balance_lookup userId={} walletId={}", userId, wallet.getId());
        return ResponseEntity.ok(walletMapper.toBalanceResponseDto(wallet));
    }

    @PostMapping("/{userId}")
    public ResponseEntity<WalletResponseDto> createWallet(@PathVariable UUID userId) {
        Wallet existing = walletRepository.findByUserId(userId).orElse(null);
        if (existing != null) {
            AUDIT_LOG.info("internal_wallet_create_idempotent userId={} walletId={}", userId, existing.getId());
            return ResponseEntity.ok(walletMapper.toWalletResponseDto(existing));
        }

        Wallet wallet = new Wallet();
        wallet.setUserId(userId);
        wallet.setBalance(BigDecimal.ZERO);
        wallet.setReservedBalance(BigDecimal.ZERO);
        wallet.setStatus(Wallet.WalletStatus.ACTIVE);

        Wallet saved = walletRepository.save(wallet);
        AUDIT_LOG.info("internal_wallet_created userId={} walletId={}", userId, saved.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(walletMapper.toWalletResponseDto(saved));
    }

    @PostMapping("/{userId}/delegated-withdrawal-tokens")
    public ResponseEntity<DelegatedWithdrawalTokenResponseDto> issueDelegatedWithdrawalToken(
            @PathVariable UUID userId,
            @Valid @RequestBody DelegatedWithdrawalTokenIssueRequestDto request
    ) {
        Wallet wallet = walletByUser(userId);
        DelegatedWithdrawalTokenResponseDto response = delegatedWithdrawalTokenLifecycleService.issueToken(
                userId,
                wallet.getId(),
                request.getAmount(),
                request.getExpiresAt(),
                request.getDelegateReference(),
                request.getIdempotencyKey());
        AUDIT_LOG.info("internal_delegated_withdrawal_token_issued userId={} walletId={} tokenId={} amount={} delegateReference={}",
                userId,
                wallet.getId(),
                response.getTokenId(),
                request.getAmount(),
                request.getDelegateReference());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{userId}/delegated-withdrawal-tokens/{delegatedToken}")
    public ResponseEntity<DelegatedWithdrawalTokenResponseDto> getDelegatedWithdrawalToken(
            @PathVariable UUID userId,
            @PathVariable String delegatedToken
    ) {
        Wallet wallet = walletByUser(userId);
        DelegatedWithdrawalTokenResponseDto response = delegatedWithdrawalTokenLifecycleService.getToken(
                userId,
                wallet.getId(),
                delegatedToken);
        AUDIT_LOG.info("internal_delegated_withdrawal_token_lookup userId={} walletId={} tokenId={} status={}",
                userId,
                wallet.getId(),
                response.getTokenId(),
                response.getStatus());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{userId}/delegated-withdrawal-tokens/{delegatedToken}/cancel")
    public ResponseEntity<DelegatedWithdrawalTokenResponseDto> cancelDelegatedWithdrawalToken(
            @PathVariable UUID userId,
            @PathVariable String delegatedToken,
            @Valid @RequestBody DelegatedWithdrawalTokenCancelRequestDto request
    ) {
        Wallet wallet = walletByUser(userId);
        DelegatedWithdrawalTokenResponseDto response = delegatedWithdrawalTokenLifecycleService.cancelToken(
                userId,
                wallet.getId(),
                delegatedToken,
                request.getReason());
        AUDIT_LOG.info("internal_delegated_withdrawal_token_cancelled userId={} walletId={} tokenId={} status={} reason={}",
                userId,
                wallet.getId(),
                response.getTokenId(),
                response.getStatus(),
                request.getReason());
        return ResponseEntity.ok(response);
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

    @PostMapping("/approvals/{approvalId}/decision")
    public ResponseEntity<FlowResultResponseDto> decideApproval(
            @PathVariable String approvalId,
            @Valid @RequestBody MakerCheckerDecisionRequestDto request
    ) {
        makerCheckerApprovalService.recordDecision(approvalId, request.isApproved(), request.getReason());
        AUDIT_LOG.info("internal_wallet_maker_checker_decision approvalId={} approved={} reason={}",
                approvalId,
                request.isApproved(),
                request.getReason());
        String message = request.isApproved() ? "Approval decision recorded" : "Rejection decision recorded";
        return ResponseEntity.ok(new FlowResultResponseDto(true, message, null, null, "wallet.maker_checker.decision_recorded"));
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