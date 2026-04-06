package com.elvo.wallet.controller;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.elvo.wallet.dto.request.DepositRequestDto;
import com.elvo.wallet.dto.request.EtcRedeemRequestDto;
import com.elvo.wallet.dto.request.FreezeUnfreezeRequestDto;
import com.elvo.wallet.dto.request.ReservationRequestDto;
import com.elvo.wallet.dto.request.TransferRequestDto;
import com.elvo.wallet.dto.request.WithdrawalRequestDto;
import com.elvo.wallet.dto.response.BalanceResponseDto;
import com.elvo.wallet.dto.response.EtcResponseDto;
import com.elvo.wallet.dto.response.FlowResultResponseDto;
import com.elvo.wallet.dto.response.LimitsResponseDto;
import com.elvo.wallet.dto.response.ReservationResponseDto;
import com.elvo.wallet.dto.response.TransactionResponseDto;
import com.elvo.wallet.dto.response.WalletResponseDto;
import com.elvo.wallet.entity.Etc;
import com.elvo.wallet.entity.Reservation;
import com.elvo.wallet.entity.Transaction;
import com.elvo.wallet.entity.Wallet;
import com.elvo.wallet.mapper.WalletMapper;
import com.elvo.wallet.repository.EtcRepository;
import com.elvo.wallet.repository.ReservationRepository;
import com.elvo.wallet.repository.TransactionRepository;
import com.elvo.wallet.repository.WalletRepository;
import com.elvo.wallet.security.EtcCodePolicyService;
import com.elvo.wallet.service.WalletService;
import com.elvo.wallet.service.model.DepositCommand;
import com.elvo.wallet.service.model.EtcCommand;
import com.elvo.wallet.service.model.ReservationCommand;
import com.elvo.wallet.service.model.TransferCommand;
import com.elvo.wallet.service.model.WalletChannel;
import com.elvo.wallet.service.model.WalletFlowResult;
import com.elvo.wallet.service.model.WithdrawalCommand;
import com.elvo.wallet.service.model.WithdrawalMode;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/wallets")
@Validated
@PreAuthorize("isAuthenticated()")
public class WalletController {

    private static final Logger LOGGER = LoggerFactory.getLogger(WalletController.class);
    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("audit.wallet.controller");

    private final WalletService walletService;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final ReservationRepository reservationRepository;
    private final EtcRepository etcRepository;
    private final WalletMapper walletMapper;
    private final EtcCodePolicyService etcCodePolicyService;

    public WalletController(WalletService walletService, WalletRepository walletRepository,
                          TransactionRepository transactionRepository, ReservationRepository reservationRepository,
                          EtcRepository etcRepository, WalletMapper walletMapper,
                          EtcCodePolicyService etcCodePolicyService) {
        this.walletService = walletService;
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.reservationRepository = reservationRepository;
        this.etcRepository = etcRepository;
        this.walletMapper = walletMapper;
        this.etcCodePolicyService = etcCodePolicyService;
    }

    /**
     * Get current user's wallet
     */
    @GetMapping("/me")
    public ResponseEntity<WalletResponseDto> getMyWallet() {
        UUID userId = getCurrentUserId();
        AUDIT_LOG.info("wallet_controller_get_wallet userId={}", userId);

        Wallet wallet = walletRepository.findByUserId(userId)
            .orElseThrow(() -> new WalletNotFoundException("Wallet not found for user: " + userId));

        return ResponseEntity.ok(walletMapper.toWalletResponseDto(wallet));
    }

    /**
     * Get current user's balance
     */
    @GetMapping("/me/balance")
    public ResponseEntity<BalanceResponseDto> getBalance() {
        UUID userId = getCurrentUserId();
        AUDIT_LOG.info("wallet_controller_get_balance userId={}", userId);

        Wallet wallet = walletRepository.findByUserId(userId)
            .orElseThrow(() -> new WalletNotFoundException("Wallet not found for user: " + userId));

        return ResponseEntity.ok(walletMapper.toBalanceResponseDto(wallet));
    }

    /**
     * Get transaction history for current user
     */
    @GetMapping("/me/transactions")
    public ResponseEntity<List<TransactionResponseDto>> getTransactions(
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "Page must be zero or greater") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "Size must be at least 1") @Max(value = 100, message = "Size must not exceed 100") int size) {

        UUID userId = getCurrentUserId();
        AUDIT_LOG.info("wallet_controller_get_transactions userId={} page={} size={}", userId, page, size);

        Wallet wallet = walletRepository.findByUserId(userId)
            .orElseThrow(() -> new WalletNotFoundException("Wallet not found for user: " + userId));

        Pageable pageable = PageRequest.of(page, size);
        Page<Transaction> transactions = transactionRepository.findByWalletIdOrderByCreatedAtDesc(wallet.getId(), pageable);

        List<TransactionResponseDto> dtos = transactions.getContent().stream()
            .map(walletMapper::toTransactionResponseDto)
            .toList();

        return ResponseEntity.ok(dtos);
    }

    /**
     * Process a deposit
     */
    @PostMapping("/deposits")
    public ResponseEntity<FlowResultResponseDto> deposit(@Valid @RequestBody DepositRequestDto request) {
        UUID userId = getCurrentUserId();
        String reference = request.getReference() != null ? request.getReference() : UUID.randomUUID().toString();

        AUDIT_LOG.info("wallet_controller_deposit userId={} amount={} channel={}", 
            userId, request.getAmount(), request.getChannel());

        Wallet wallet = walletRepository.findByUserId(userId)
            .orElseThrow(() -> new WalletNotFoundException("Wallet not found for user: " + userId));

        try {
            WalletChannel channel = WalletChannel.valueOf(request.getChannel());
            DepositCommand command = new DepositCommand(
                wallet.getId(),
                userId,
                request.getAmount(),
                channel,
                request.getIdempotencyKey(),
                reference,
                channel == WalletChannel.AGENT, // agentFloatAvailable
                request.getMobileCallbackReference()
            );

            WalletFlowResult result = walletService.processDeposit(command);
            FlowResultResponseDto response = walletMapper.toFlowResultResponseDto(result);

            if (result.success()) {
                AUDIT_LOG.info("wallet_deposit_success userId={} amount={} walletId={} transactionId={}",
                    userId, request.getAmount(), wallet.getId(), result.transactionId());
                return ResponseEntity.status(HttpStatus.CREATED).body(response);
            } else {
                AUDIT_LOG.warn("wallet_deposit_failed userId={} amount={} reason={}", 
                    userId, request.getAmount(), result.message());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
        } catch (IllegalArgumentException e) {
            LOGGER.error("Invalid channel: {}", request.getChannel(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new FlowResultResponseDto(false, "Invalid channel: " + request.getChannel(), 
                    wallet.getId(), null, "wallet.deposit.failed"));
        }
    }

    /**
     * Process a withdrawal
     */
    @PostMapping("/withdrawals")
    public ResponseEntity<FlowResultResponseDto> withdraw(@Valid @RequestBody WithdrawalRequestDto request) {
        UUID userId = getCurrentUserId();
        String reference = request.getReference() != null ? request.getReference() : UUID.randomUUID().toString();

        AUDIT_LOG.info("wallet_controller_withdrawal userId={} amount={} mode={}", 
            userId, request.getAmount(), request.getMode());

        Wallet wallet = walletRepository.findByUserId(userId)
            .orElseThrow(() -> new WalletNotFoundException("Wallet not found for user: " + userId));

        try {
            WithdrawalMode mode = WithdrawalMode.valueOf(request.getMode());
            WithdrawalCommand command = new WithdrawalCommand(
                wallet.getId(),
                userId,
                request.getAmount(),
                mode,
                request.getTargetNumber(),
                request.getEspCode(),
                request.getEacCode(),
                request.getIdempotencyKey(),
                reference,
                request.getStepUpMethod(),
                request.getStepUpToken(),
                request.getTransactionChallengeToken()
            );

            WalletFlowResult result = walletService.processWithdrawal(command);
            FlowResultResponseDto response = walletMapper.toFlowResultResponseDto(result);

            if (result.success()) {
                AUDIT_LOG.info("wallet_withdrawal_success userId={} amount={} walletId={} transactionId={}",
                    userId, request.getAmount(), wallet.getId(), result.transactionId());
                return ResponseEntity.status(HttpStatus.CREATED).body(response);
            } else {
                AUDIT_LOG.warn("wallet_withdrawal_failed userId={} amount={} reason={}",
                    userId, request.getAmount(), result.message());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
        } catch (IllegalArgumentException e) {
            LOGGER.error("Invalid withdrawal mode: {}", request.getMode(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new FlowResultResponseDto(false, "Invalid withdrawal mode: " + request.getMode(),
                    wallet.getId(), null, "wallet.withdrawal.failed"));
        }
    }

    /**
     * Process a transfer to another wallet
     */
    @PostMapping("/transfers")
    public ResponseEntity<FlowResultResponseDto> transfer(@Valid @RequestBody TransferRequestDto request) {
        UUID userId = getCurrentUserId();
        String reference = request.getReference() != null ? request.getReference() : UUID.randomUUID().toString();

        AUDIT_LOG.info("wallet_controller_transfer userId={} targetWalletId={} amount={}", 
            userId, request.getTargetWalletId(), request.getAmount());

        Wallet sourceWallet = walletRepository.findByUserId(userId)
            .orElseThrow(() -> new WalletNotFoundException("Wallet not found for user: " + userId));

        Wallet targetWallet = walletRepository.findById(request.getTargetWalletId())
            .orElseThrow(() -> new WalletNotFoundException("Target wallet not found: " + request.getTargetWalletId()));

        TransferCommand command = new TransferCommand(
            sourceWallet.getId(),
            targetWallet.getId(),
            userId,
            request.getAmount(),
            request.getIdempotencyKey(),
            reference,
            request.getStepUpMethod(),
            request.getStepUpToken(),
            request.getTransactionChallengeToken()
        );

        WalletFlowResult result = walletService.processTransfer(command);
        FlowResultResponseDto response = walletMapper.toFlowResultResponseDto(result);

        if (result.success()) {
            AUDIT_LOG.info("wallet_transfer_success userId={} sourceWalletId={} targetWalletId={} amount={} walletId={} transactionId={}",
                userId, sourceWallet.getId(), request.getTargetWalletId(), request.getAmount(),
                sourceWallet.getId(), result.transactionId());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } else {
            AUDIT_LOG.warn("wallet_transfer_failed userId={} sourceWalletId={} targetWalletId={} amount={} reason={}",
                userId, sourceWallet.getId(), request.getTargetWalletId(), request.getAmount(), result.message());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    /**
     * Get withdrawal codes (ETC codes)
     */
    @GetMapping("/withdrawal-codes")
    public ResponseEntity<List<EtcResponseDto>> getWithdrawalCodes() {
        UUID userId = getCurrentUserId();
        AUDIT_LOG.info("wallet_controller_get_withdrawal_codes userId={}", userId);

        Wallet wallet = walletRepository.findByUserId(userId)
            .orElseThrow(() -> new WalletNotFoundException("Wallet not found for user: " + userId));

        List<Etc> codes = etcRepository.findByWalletIdOrderByCreatedAtDesc(wallet.getId());
        List<EtcResponseDto> dtos = codes.stream()
            .map(walletMapper::toEtcResponseDto)
            .toList();

        return ResponseEntity.ok(dtos);
    }

    /**
     * Generate a withdrawal code (ETC)
     */
    @PostMapping("/withdrawal-codes")
    public ResponseEntity<FlowResultResponseDto> generateWithdrawalCode(@Valid @RequestBody ReservationRequestDto request) {
        UUID userId = getCurrentUserId();

        AUDIT_LOG.info("wallet_controller_generate_withdrawal_code userId={} idempotencyKey={}", 
            userId, request.getIdempotencyKey());

        Wallet wallet = walletRepository.findByUserId(userId)
            .orElseThrow(() -> new WalletNotFoundException("Wallet not found for user: " + userId));

        String code = etcCodePolicyService.generateSecureCode();

        EtcCommand command = new EtcCommand(
            wallet.getId(),
            userId,
            code,
            request.getExpiryDate(),
            request.getIdempotencyKey()
        );

        WalletFlowResult result = walletService.generateEtc(command);
        FlowResultResponseDto response = walletMapper.toFlowResultResponseDto(result);

        if (result.success()) {
            AUDIT_LOG.info("wallet_etc_generated userId={} code={} walletId={}",
                userId, code, wallet.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } else {
            AUDIT_LOG.warn("wallet_etc_generation_failed userId={} reason={}", userId, result.message());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    /**
     * Redeem a withdrawal code (ETC)
     */
    @PostMapping("/withdrawal-codes/{code}/redeem")
    public ResponseEntity<FlowResultResponseDto> redeemWithdrawalCode(
            @PathVariable String code,
            @Valid @RequestBody EtcRedeemRequestDto request,
            HttpServletRequest httpRequest) {
        
        UUID userId = getCurrentUserId();
        AUDIT_LOG.info("wallet_controller_redeem_withdrawal_code userId={} code={}", userId, code);

        Wallet wallet = walletRepository.findByUserId(userId)
            .orElseThrow(() -> new WalletNotFoundException("Wallet not found for user: " + userId));

        String deviceId = httpRequest.getHeader("X-Device-Id");
        String sourceIp = httpRequest.getRemoteAddr();
        WalletFlowResult result = walletService.redeemEtc(code, request.getIdempotencyKey(), deviceId, sourceIp);
        FlowResultResponseDto response = walletMapper.toFlowResultResponseDto(result);

        if (result.success()) {
            AUDIT_LOG.info("wallet_etc_redeemed userId={} code={} walletId={}",
                userId, code, wallet.getId());
            return ResponseEntity.ok(response);
        } else {
            AUDIT_LOG.warn("wallet_etc_redeem_failed userId={} code={} reason={}", userId, code, result.message());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    /**
     * Get wallet limits and usage
     */
    @GetMapping("/me/limits")
    public ResponseEntity<LimitsResponseDto> getLimits() {
        UUID userId = getCurrentUserId();
        AUDIT_LOG.info("wallet_controller_get_limits userId={}", userId);

        Wallet wallet = walletRepository.findByUserId(userId)
            .orElseThrow(() -> new WalletNotFoundException("Wallet not found for user: " + userId));

        // Get limits and usage from limit enforcement service
        // These are hardcoded defaults; in production would be configurable
        LimitsResponseDto limits = new LimitsResponseDto(
            java.math.BigDecimal.valueOf(5000),  // daily
            java.math.BigDecimal.valueOf(50000), // monthly
            java.math.BigDecimal.valueOf(2000),  // transfer
            java.math.BigDecimal.valueOf(1000),  // withdrawal
            java.math.BigDecimal.valueOf(10000), // deposit
            java.math.BigDecimal.ZERO,           // daily used (would fetch from service)
            java.math.BigDecimal.ZERO            // monthly used (would fetch from service)
        );

        return ResponseEntity.ok(limits);
    }

    /**
     * Freeze wallet
     */
    @PostMapping("/me/freeze")
    public ResponseEntity<FlowResultResponseDto> freezeWallet(@Valid @RequestBody FreezeUnfreezeRequestDto request) {
        UUID userId = getCurrentUserId();
        String reason = request.getReason() != null ? request.getReason() : "User action";

        AUDIT_LOG.info("wallet_controller_freeze_wallet userId={} reason={}", userId, reason);

        Wallet wallet = walletRepository.findByUserId(userId)
            .orElseThrow(() -> new WalletNotFoundException("Wallet not found for user: " + userId));

        WalletFlowResult result = walletService.freezeWallet(wallet.getId(), reason);
        FlowResultResponseDto response = walletMapper.toFlowResultResponseDto(result);

        if (result.success()) {
            AUDIT_LOG.info("wallet_frozen userId={} walletId={} reason={}", userId, wallet.getId(), reason);
            return ResponseEntity.ok(response);
        } else {
            AUDIT_LOG.warn("wallet_freeze_failed userId={} reason={}", userId, result.message());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    /**
     * Unfreeze wallet
     */
    @PostMapping("/me/unfreeze")
    public ResponseEntity<FlowResultResponseDto> unfreezeWallet(@Valid @RequestBody FreezeUnfreezeRequestDto request) {
        UUID userId = getCurrentUserId();
        String reason = request.getReason() != null ? request.getReason() : "User action";

        AUDIT_LOG.info("wallet_controller_unfreeze_wallet userId={} reason={}", userId, reason);

        Wallet wallet = walletRepository.findByUserId(userId)
            .orElseThrow(() -> new WalletNotFoundException("Wallet not found for user: " + userId));

        WalletFlowResult result = walletService.unfreezeWallet(wallet.getId(), reason);
        FlowResultResponseDto response = walletMapper.toFlowResultResponseDto(result);

        if (result.success()) {
            AUDIT_LOG.info("wallet_unfrozen userId={} walletId={} reason={}", userId, wallet.getId(), reason);
            return ResponseEntity.ok(response);
        } else {
            AUDIT_LOG.warn("wallet_unfreeze_failed userId={} reason={}", userId, result.message());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    /**
     * Get reservations for current user
     */
    @GetMapping("/me/reservations")
    public ResponseEntity<List<ReservationResponseDto>> getReservations() {
        UUID userId = getCurrentUserId();
        AUDIT_LOG.info("wallet_controller_get_reservations userId={}", userId);

        Wallet wallet = walletRepository.findByUserId(userId)
            .orElseThrow(() -> new WalletNotFoundException("Wallet not found for user: " + userId));

        List<Reservation> reservations = reservationRepository.findByWalletIdOrderByCreatedAtDesc(wallet.getId());
        List<ReservationResponseDto> dtos = reservations.stream()
            .map(walletMapper::toReservationResponseDto)
            .toList();

        return ResponseEntity.ok(dtos);
    }

    /**
     * Create a reservation
     */
    @PostMapping("/me/reservations")
    public ResponseEntity<FlowResultResponseDto> createReservation(@Valid @RequestBody ReservationRequestDto request) {
        UUID userId = getCurrentUserId();

        AUDIT_LOG.info("wallet_controller_create_reservation userId={} amount={} expiryDate={}", 
            userId, request.getAmount(), request.getExpiryDate());

        Wallet wallet = walletRepository.findByUserId(userId)
            .orElseThrow(() -> new WalletNotFoundException("Wallet not found for user: " + userId));

        ReservationCommand command = new ReservationCommand(
            wallet.getId(),
            userId,
            request.getAmount(),
            request.getExpiryDate(),
            request.getIdempotencyKey(),
            UUID.randomUUID().toString()
        );

        WalletFlowResult result = walletService.createReservation(command);
        FlowResultResponseDto response = walletMapper.toFlowResultResponseDto(result);

        if (result.success()) {
            AUDIT_LOG.info("wallet_reservation_created userId={} amount={} walletId={} reservationId={}",
                userId, request.getAmount(), wallet.getId(), result.transactionId());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } else {
            AUDIT_LOG.warn("wallet_reservation_creation_failed userId={} amount={} reason={}",
                userId, request.getAmount(), result.message());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    /**
     * Release a reservation
     */
    @PostMapping("/me/reservations/{id}/release")
    public ResponseEntity<FlowResultResponseDto> releaseReservation(
            @PathVariable UUID id,
            @RequestBody FreezeUnfreezeRequestDto request) {
        
        UUID userId = getCurrentUserId();
        String idempotencyKey = request.getReason() != null ? request.getReason() : UUID.randomUUID().toString();

        AUDIT_LOG.info("wallet_controller_release_reservation userId={} reservationId={}", userId, id);

        Wallet wallet = walletRepository.findByUserId(userId)
            .orElseThrow(() -> new WalletNotFoundException("Wallet not found for user: " + userId));

        WalletFlowResult result = walletService.releaseReservation(id, idempotencyKey);
        FlowResultResponseDto response = walletMapper.toFlowResultResponseDto(result);

        if (result.success()) {
            AUDIT_LOG.info("wallet_reservation_released userId={} reservationId={} walletId={}",
                userId, id, wallet.getId());
            return ResponseEntity.ok(response);
        } else {
            AUDIT_LOG.warn("wallet_reservation_release_failed userId={} reservationId={} reason={}",
                userId, id, result.message());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    /**
     * Confirm a reservation (confirm debit)
     */
    @PostMapping("/me/reservations/{id}/confirm")
    public ResponseEntity<FlowResultResponseDto> confirmReservation(
            @PathVariable UUID id,
            @RequestBody FreezeUnfreezeRequestDto request) {
        
        UUID userId = getCurrentUserId();
        String idempotencyKey = request.getReason() != null ? request.getReason() : UUID.randomUUID().toString();

        AUDIT_LOG.info("wallet_controller_confirm_reservation userId={} reservationId={}", userId, id);

        Wallet wallet = walletRepository.findByUserId(userId)
            .orElseThrow(() -> new WalletNotFoundException("Wallet not found for user: " + userId));

        WalletFlowResult result = walletService.confirmReservation(id, idempotencyKey);
        FlowResultResponseDto response = walletMapper.toFlowResultResponseDto(result);

        if (result.success()) {
            AUDIT_LOG.info("wallet_reservation_confirmed userId={} reservationId={} walletId={}",
                userId, id, wallet.getId());
            return ResponseEntity.ok(response);
        } else {
            AUDIT_LOG.warn("wallet_reservation_confirm_failed userId={} reservationId={} reason={}",
                userId, id, result.message());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    /**
     * Extract current authenticated user ID
     */
    private UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("User not authenticated");
        }

        String authenticatedName = authentication.getName();
        if (authenticatedName != null && !authenticatedName.isBlank()) {
            try {
                return UUID.fromString(authenticatedName);
            } catch (IllegalArgumentException ex) {
                LOGGER.error("Invalid user ID format in authentication name: {}", authenticatedName);
            }
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof String principalName) {
            try {
                return UUID.fromString(principalName);
            } catch (IllegalArgumentException e) {
                LOGGER.error("Invalid user ID format in principal: {}", principalName);
                throw new UnauthorizedException("Invalid user ID format");
            }
        }

        throw new UnauthorizedException("Unable to extract user ID from authentication");
    }

    // Custom exceptions
    public static class WalletNotFoundException extends RuntimeException {
        public WalletNotFoundException(String message) {
            super(message);
        }
    }

    public static class UnauthorizedException extends RuntimeException {
        public UnauthorizedException(String message) {
            super(message);
        }
    }
}
