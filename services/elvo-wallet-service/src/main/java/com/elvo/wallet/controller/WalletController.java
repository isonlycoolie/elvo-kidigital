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
import com.elvo.wallet.dto.request.DeviceFreeWithdrawalRequestDto;
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
import com.elvo.wallet.monitoring.WalletMetricsRecorder;
import com.elvo.wallet.monitoring.SecurityAlertStreamingService;
import com.elvo.wallet.repository.EtcRepository;
import com.elvo.wallet.repository.ReservationRepository;
import com.elvo.wallet.repository.TransactionRepository;
import com.elvo.wallet.repository.WalletRepository;
import com.elvo.wallet.security.DestinationRiskService;
import com.elvo.wallet.security.DeviceLocationRiskService;
import com.elvo.wallet.security.EtcCodePolicyService;
import com.elvo.wallet.security.FraudRulesEngine;
import com.elvo.wallet.security.IpGeovelocityRiskService;
import com.elvo.wallet.security.MakerCheckerApprovalService;
import com.elvo.wallet.security.ApiAbuseProtectionService;
import com.elvo.wallet.security.AmlCaseWorkflowService;
import com.elvo.wallet.security.EmergencyControlService;
import com.elvo.wallet.security.SanctionsScreeningService;
import com.elvo.wallet.security.UserJwtPrincipal;
import com.elvo.wallet.security.WalletOperationRateLimitService;
import com.elvo.wallet.service.WalletService;
import com.elvo.wallet.service.impl.WalletLimitEnforcementService;
import com.elvo.wallet.service.model.DepositCommand;
import com.elvo.wallet.service.model.EtcCommand;
import com.elvo.wallet.service.model.ReservationCommand;
import com.elvo.wallet.service.model.TransferCommand;
import com.elvo.wallet.service.model.WalletChannel;
import com.elvo.wallet.service.model.WalletFlowResult;
import com.elvo.wallet.service.model.WithdrawalCommand;
import com.elvo.wallet.service.model.WithdrawalMode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/wallets")
@Validated
@PreAuthorize("isAuthenticated()")
public class WalletController {

    private static final Logger LOGGER = LoggerFactory.getLogger(WalletController.class);
    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("audit.wallet.controller");
    private static final String MOBILE_CALLBACK_SIGNATURE_HEADER = "X-Mobile-Callback-Signature";
    private static final String MOBILE_CALLBACK_TIMESTAMP_HEADER = "X-Mobile-Callback-Timestamp";
    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    private final WalletService walletService;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final ReservationRepository reservationRepository;
    private final EtcRepository etcRepository;
    private final WalletMapper walletMapper;
    private final EtcCodePolicyService etcCodePolicyService;
    private final WalletOperationRateLimitService operationRateLimitService;
    private final DeviceLocationRiskService deviceLocationRiskService;
    private final DestinationRiskService destinationRiskService;
    private final IpGeovelocityRiskService ipGeovelocityRiskService;
    private final FraudRulesEngine fraudRulesEngine;
    private final MakerCheckerApprovalService makerCheckerApprovalService;
    private final SecurityAlertStreamingService securityAlertStreamingService;
    private final ApiAbuseProtectionService apiAbuseProtectionService;
    private final EmergencyControlService emergencyControlService;
    private final SanctionsScreeningService sanctionsScreeningService;
    private final AmlCaseWorkflowService amlCaseWorkflowService;
    private final WalletMetricsRecorder walletMetricsRecorder;
    private final WalletLimitEnforcementService walletLimitEnforcementService;

    public WalletController(WalletService walletService, WalletRepository walletRepository,
                          TransactionRepository transactionRepository, ReservationRepository reservationRepository,
                          EtcRepository etcRepository, WalletMapper walletMapper,
                          EtcCodePolicyService etcCodePolicyService,
                          WalletOperationRateLimitService operationRateLimitService,
                          DeviceLocationRiskService deviceLocationRiskService,
                          DestinationRiskService destinationRiskService,
                          IpGeovelocityRiskService ipGeovelocityRiskService,
                          FraudRulesEngine fraudRulesEngine,
                          MakerCheckerApprovalService makerCheckerApprovalService,
                          SecurityAlertStreamingService securityAlertStreamingService,
                          ApiAbuseProtectionService apiAbuseProtectionService,
                          EmergencyControlService emergencyControlService,
                          SanctionsScreeningService sanctionsScreeningService,
                          AmlCaseWorkflowService amlCaseWorkflowService,
                          WalletMetricsRecorder walletMetricsRecorder,
                          WalletLimitEnforcementService walletLimitEnforcementService) {
        this.walletService = walletService;
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.reservationRepository = reservationRepository;
        this.etcRepository = etcRepository;
        this.walletMapper = walletMapper;
        this.etcCodePolicyService = etcCodePolicyService;
        this.operationRateLimitService = operationRateLimitService;
        this.deviceLocationRiskService = deviceLocationRiskService;
        this.destinationRiskService = destinationRiskService;
        this.ipGeovelocityRiskService = ipGeovelocityRiskService;
        this.fraudRulesEngine = fraudRulesEngine;
        this.makerCheckerApprovalService = makerCheckerApprovalService;
        this.securityAlertStreamingService = securityAlertStreamingService;
        this.apiAbuseProtectionService = apiAbuseProtectionService;
        this.emergencyControlService = emergencyControlService;
        this.sanctionsScreeningService = sanctionsScreeningService;
        this.amlCaseWorkflowService = amlCaseWorkflowService;
        this.walletMetricsRecorder = walletMetricsRecorder;
        this.walletLimitEnforcementService = walletLimitEnforcementService;
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
    public ResponseEntity<FlowResultResponseDto> deposit(@Valid @RequestBody DepositRequestDto request,
                                                         HttpServletRequest httpRequest) {
        UUID userId = getCurrentUserId();
        String reference = request.getReference() != null ? request.getReference() : UUID.randomUUID().toString();

        AUDIT_LOG.info("wallet_controller_deposit userId={} amount={} channel={}", 
            userId, request.getAmount(), request.getChannel());

        Wallet wallet = walletRepository.findByUserId(userId)
            .orElseThrow(() -> new WalletNotFoundException("Wallet not found for user: " + userId));

        SanctionsScreeningService.ScreeningDecision sanctionsDecision = sanctionsScreeningService.evaluate(userId, null);
        if (!sanctionsDecision.allowed()) {
            walletMetricsRecorder.recordSecurityControl("sanctions", true);
            createAmlCase("SANCTIONS_OR_BLACKLIST", userId, wallet.getId(), "deposit", sanctionsDecision.reason(), java.util.Map.of());
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new FlowResultResponseDto(false, sanctionsDecision.reason(), wallet.getId(), null, "wallet.deposit.failed"));
        }
        walletMetricsRecorder.recordSecurityControl("sanctions", false);

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
                request.getMobileCallbackReference(),
                httpRequest.getHeader(MOBILE_CALLBACK_SIGNATURE_HEADER),
                parseLongHeader(httpRequest.getHeader(MOBILE_CALLBACK_TIMESTAMP_HEADER)),
                resolveClientIp(httpRequest)
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
    public ResponseEntity<FlowResultResponseDto> withdraw(@Valid @RequestBody WithdrawalRequestDto request,
                                                          HttpServletRequest httpRequest) {
        UUID userId = getCurrentUserId();
        String reference = request.getReference() != null ? request.getReference() : UUID.randomUUID().toString();

        AUDIT_LOG.info("wallet_controller_withdrawal userId={} amount={} mode={}", 
            userId, request.getAmount(), request.getMode());

        Wallet wallet = walletRepository.findByUserId(userId)
            .orElseThrow(() -> new WalletNotFoundException("Wallet not found for user: " + userId));

        SanctionsScreeningService.ScreeningDecision sanctionsDecision = sanctionsScreeningService.evaluate(userId, request.getTargetNumber());
        if (!sanctionsDecision.allowed()) {
            walletMetricsRecorder.recordSecurityControl("sanctions", true);
            createAmlCase("SANCTIONS_OR_BLACKLIST", userId, wallet.getId(), "withdrawal", sanctionsDecision.reason(), java.util.Map.of(
                "targetNumber", String.valueOf(request.getTargetNumber())));
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new FlowResultResponseDto(false, sanctionsDecision.reason(), wallet.getId(), null, "wallet.withdrawal.failed"));
        }
        walletMetricsRecorder.recordSecurityControl("sanctions", false);

        String sourceIp = resolveClientIp(httpRequest);
        String deviceId = httpRequest.getHeader("X-Device-Id");
        ApiAbuseProtectionService.AbuseDecision abuseDecision = apiAbuseProtectionService.evaluate(userId, sourceIp, deviceId);
        if (!abuseDecision.allowed()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new FlowResultResponseDto(false, abuseDecision.reason(), wallet.getId(), null, "wallet.withdrawal.failed"));
        }

        WalletOperationRateLimitService.RateLimitResult withdrawalRateLimit = operationRateLimitService.enforce(
            WalletOperationRateLimitService.Operation.WITHDRAWAL,
            userId,
            sourceIp,
            deviceId,
            request.getTargetNumber());
        if (!withdrawalRateLimit.allowed()) {
            apiAbuseProtectionService.recordViolation(userId, sourceIp, deviceId);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new FlowResultResponseDto(false, "Withdrawal rate limit exceeded", wallet.getId(), null, "wallet.withdrawal.failed"));
        }

        String withdrawalDeviceId = deviceId;
        String withdrawalLocationHint = resolveLocationHint(httpRequest);
        IpGeovelocityRiskService.RiskDecision withdrawalIpDecision = ipGeovelocityRiskService.evaluate(
            userId,
            httpRequest.getRemoteAddr(),
            withdrawalLocationHint);
        if (withdrawalIpDecision.blocked()) {
            securityAlertStreamingService.stream("wallet.security.ip.blocked", "HIGH", userId, java.util.Map.of(
                "operation", "withdrawal",
                "reason", withdrawalIpDecision.reason()));
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new FlowResultResponseDto(false, withdrawalIpDecision.reason(), wallet.getId(), null, "wallet.withdrawal.failed"));
        }
        if (withdrawalIpDecision.requiresVerification() && (request.getStepUpToken() == null || request.getStepUpToken().isBlank())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new FlowResultResponseDto(false, withdrawalIpDecision.reason(), wallet.getId(), null, "wallet.withdrawal.failed"));
        }
        boolean withdrawalRisky = deviceLocationRiskService.requiresAdditionalVerification(userId, withdrawalDeviceId, withdrawalLocationHint);
        if (withdrawalRisky && (request.getStepUpToken() == null || request.getStepUpToken().isBlank())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new FlowResultResponseDto(false, "Device/location verification required", wallet.getId(), null, "wallet.withdrawal.failed"));
        }

        DestinationRiskService.DestinationRiskDecision destinationRisk = destinationRiskService.evaluate(
            userId,
            request.getTargetNumber(),
            request.getAmount());
        if (destinationRisk.blocked()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new FlowResultResponseDto(false, destinationRisk.reason(), wallet.getId(), null, "wallet.withdrawal.failed"));
        }
        if (destinationRisk.requiresVerification() && (request.getStepUpToken() == null || request.getStepUpToken().isBlank())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new FlowResultResponseDto(false, destinationRisk.reason(), wallet.getId(), null, "wallet.withdrawal.failed"));
        }

        FraudRulesEngine.FraudDecision withdrawalFraudDecision = fraudRulesEngine.evaluate(
            FraudRulesEngine.Operation.WITHDRAWAL,
            userId,
            request.getAmount(),
            request.getTargetNumber());
        if (withdrawalFraudDecision.blocked()) {
            walletMetricsRecorder.recordSecurityControl("fraud_rules", true);
            createAmlCase("FRAUD_RULE_BLOCK", userId, wallet.getId(), "withdrawal", withdrawalFraudDecision.reason(), java.util.Map.of(
                "targetNumber", String.valueOf(request.getTargetNumber()),
                "amount", String.valueOf(request.getAmount())));
            securityAlertStreamingService.stream("wallet.security.fraud.blocked", "HIGH", userId, java.util.Map.of(
                "operation", "withdrawal",
                "reason", withdrawalFraudDecision.reason()));
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new FlowResultResponseDto(false, withdrawalFraudDecision.reason(), wallet.getId(), null, "wallet.withdrawal.failed"));
        }
        walletMetricsRecorder.recordSecurityControl("fraud_rules", false);
        if (withdrawalFraudDecision.requiresVerification() && (request.getStepUpToken() == null || request.getStepUpToken().isBlank())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new FlowResultResponseDto(false, withdrawalFraudDecision.reason(), wallet.getId(), null, "wallet.withdrawal.failed"));
        }

        MakerCheckerApprovalService.ApprovalDecision withdrawalApprovalDecision = makerCheckerApprovalService.evaluate(
            MakerCheckerApprovalService.Operation.WITHDRAWAL,
            userId,
            request.getAmount(),
            httpRequest.getHeader("X-Approval-Token"));
        if (withdrawalApprovalDecision.rejected()) {
            securityAlertStreamingService.stream("wallet.security.maker_checker.rejected", "MEDIUM", userId, java.util.Map.of(
                "operation", "withdrawal",
                "reason", withdrawalApprovalDecision.reason()));
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new FlowResultResponseDto(false, withdrawalApprovalDecision.reason(), wallet.getId(), null, "wallet.withdrawal.failed"));
        }
        if (withdrawalApprovalDecision.pending()) {
            securityAlertStreamingService.stream("wallet.security.maker_checker.pending", "MEDIUM", userId, java.util.Map.of(
                "operation", "withdrawal",
                "approvalId", withdrawalApprovalDecision.approvalId()));
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new FlowResultResponseDto(false, withdrawalApprovalDecision.reason(), wallet.getId(),
                    UUID.fromString(withdrawalApprovalDecision.approvalId()), "wallet.withdrawal.pending_approval"));
        }

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
                deviceLocationRiskService.markTrusted(userId, withdrawalDeviceId, withdrawalLocationHint);
                destinationRiskService.markTrusted(userId, request.getTargetNumber());
                AUDIT_LOG.info("wallet_withdrawal_success userId={} amount={} walletId={} transactionId={}",
                    userId, request.getAmount(), wallet.getId(), result.transactionId());
                apiAbuseProtectionService.recordSuccess(userId, sourceIp, deviceId);
                return ResponseEntity.status(HttpStatus.CREATED).body(response);
            } else {
                apiAbuseProtectionService.recordViolation(userId, sourceIp, deviceId);
                destinationRiskService.recordFailure(userId, request.getTargetNumber());
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
     * Process a dedicated device-free withdrawal flow.
     */
    @PostMapping("/withdrawals/device-free")
    public ResponseEntity<FlowResultResponseDto> withdrawDeviceFree(@Valid @RequestBody DeviceFreeWithdrawalRequestDto request,
                                                                    HttpServletRequest httpRequest) {
        WithdrawalRequestDto withdrawalRequest = new WithdrawalRequestDto();
        withdrawalRequest.setAmount(request.getAmount());
        withdrawalRequest.setMode(WithdrawalMode.DEVICE_FREE.name());
        withdrawalRequest.setTargetNumber(request.getTargetNumber());
        withdrawalRequest.setEspCode(request.getEspCode());
        withdrawalRequest.setEacCode(request.getEacCode());
        withdrawalRequest.setIdempotencyKey(request.getIdempotencyKey());
        withdrawalRequest.setReference(request.getReference());
        withdrawalRequest.setStepUpMethod(request.getStepUpMethod());
        withdrawalRequest.setStepUpToken(request.getStepUpToken());
        withdrawalRequest.setTransactionChallengeToken(request.getTransactionChallengeToken());
        return withdraw(withdrawalRequest, httpRequest);
    }

    /**
     * Process a transfer to another wallet
     */
    @PostMapping("/transfers")
    public ResponseEntity<FlowResultResponseDto> transfer(@Valid @RequestBody TransferRequestDto request,
                                                          HttpServletRequest httpRequest) {
        UUID userId = getCurrentUserId();
        String reference = request.getReference() != null ? request.getReference() : UUID.randomUUID().toString();

        AUDIT_LOG.info("wallet_controller_transfer userId={} targetWalletId={} amount={}", 
            userId, request.getTargetWalletId(), request.getAmount());

        Wallet sourceWallet = walletRepository.findByUserId(userId)
            .orElseThrow(() -> new WalletNotFoundException("Wallet not found for user: " + userId));

        SanctionsScreeningService.ScreeningDecision sanctionsDecision = sanctionsScreeningService.evaluate(
            userId,
            request.getTargetWalletId() == null ? null : request.getTargetWalletId().toString());
        if (!sanctionsDecision.allowed()) {
            walletMetricsRecorder.recordSecurityControl("sanctions", true);
            createAmlCase("SANCTIONS_OR_BLACKLIST", userId, sourceWallet.getId(), "transfer", sanctionsDecision.reason(), java.util.Map.of(
                "targetWalletId", String.valueOf(request.getTargetWalletId())));
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new FlowResultResponseDto(false, sanctionsDecision.reason(), sourceWallet.getId(), null, "wallet.transfer.failed"));
        }
        walletMetricsRecorder.recordSecurityControl("sanctions", false);

        ResponseEntity<FlowResultResponseDto> emergencyBlocked = blockIfEmergencyControlTriggered(sourceWallet.getId(), "wallet.transfer.failed");
        if (emergencyBlocked != null) {
            return emergencyBlocked;
        }

        String sourceIp = resolveClientIp(httpRequest);
        String deviceId = httpRequest.getHeader("X-Device-Id");
        ApiAbuseProtectionService.AbuseDecision abuseDecision = apiAbuseProtectionService.evaluate(userId, sourceIp, deviceId);
        if (!abuseDecision.allowed()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new FlowResultResponseDto(false, abuseDecision.reason(), sourceWallet.getId(), null, "wallet.transfer.failed"));
        }

        WalletOperationRateLimitService.RateLimitResult transferRateLimit = operationRateLimitService.enforce(
            WalletOperationRateLimitService.Operation.TRANSFER,
            userId,
            sourceIp,
            deviceId,
            null);
        if (!transferRateLimit.allowed()) {
            apiAbuseProtectionService.recordViolation(userId, sourceIp, deviceId);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new FlowResultResponseDto(false, "Transfer rate limit exceeded", sourceWallet.getId(), null, "wallet.transfer.failed"));
        }

        String transferDeviceId = deviceId;
        String transferLocationHint = resolveLocationHint(httpRequest);
        IpGeovelocityRiskService.RiskDecision transferIpDecision = ipGeovelocityRiskService.evaluate(
            userId,
            httpRequest.getRemoteAddr(),
            transferLocationHint);
        if (transferIpDecision.blocked()) {
            securityAlertStreamingService.stream("wallet.security.ip.blocked", "HIGH", userId, java.util.Map.of(
                "operation", "transfer",
                "reason", transferIpDecision.reason()));
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new FlowResultResponseDto(false, transferIpDecision.reason(), sourceWallet.getId(), null, "wallet.transfer.failed"));
        }
        if (transferIpDecision.requiresVerification() && (request.getStepUpToken() == null || request.getStepUpToken().isBlank())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new FlowResultResponseDto(false, transferIpDecision.reason(), sourceWallet.getId(), null, "wallet.transfer.failed"));
        }
        boolean transferRisky = deviceLocationRiskService.requiresAdditionalVerification(userId, transferDeviceId, transferLocationHint);
        if (transferRisky && (request.getStepUpToken() == null || request.getStepUpToken().isBlank())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new FlowResultResponseDto(false, "Device/location verification required", sourceWallet.getId(), null, "wallet.transfer.failed"));
        }

        FraudRulesEngine.FraudDecision transferFraudDecision = fraudRulesEngine.evaluate(
            FraudRulesEngine.Operation.TRANSFER,
            userId,
            request.getAmount(),
            request.getTargetWalletId() == null ? null : request.getTargetWalletId().toString());
        if (transferFraudDecision.blocked()) {
            walletMetricsRecorder.recordSecurityControl("fraud_rules", true);
            createAmlCase("FRAUD_RULE_BLOCK", userId, sourceWallet.getId(), "transfer", transferFraudDecision.reason(), java.util.Map.of(
                "targetWalletId", String.valueOf(request.getTargetWalletId()),
                "amount", String.valueOf(request.getAmount())));
            securityAlertStreamingService.stream("wallet.security.fraud.blocked", "HIGH", userId, java.util.Map.of(
                "operation", "transfer",
                "reason", transferFraudDecision.reason()));
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new FlowResultResponseDto(false, transferFraudDecision.reason(), sourceWallet.getId(), null, "wallet.transfer.failed"));
        }
        walletMetricsRecorder.recordSecurityControl("fraud_rules", false);
        if (transferFraudDecision.requiresVerification() && (request.getStepUpToken() == null || request.getStepUpToken().isBlank())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new FlowResultResponseDto(false, transferFraudDecision.reason(), sourceWallet.getId(), null, "wallet.transfer.failed"));
        }

        MakerCheckerApprovalService.ApprovalDecision transferApprovalDecision = makerCheckerApprovalService.evaluate(
            MakerCheckerApprovalService.Operation.TRANSFER,
            userId,
            request.getAmount(),
            httpRequest.getHeader("X-Approval-Token"));
        if (transferApprovalDecision.rejected()) {
            securityAlertStreamingService.stream("wallet.security.maker_checker.rejected", "MEDIUM", userId, java.util.Map.of(
                "operation", "transfer",
                "reason", transferApprovalDecision.reason()));
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new FlowResultResponseDto(false, transferApprovalDecision.reason(), sourceWallet.getId(), null, "wallet.transfer.failed"));
        }
        if (transferApprovalDecision.pending()) {
            securityAlertStreamingService.stream("wallet.security.maker_checker.pending", "MEDIUM", userId, java.util.Map.of(
                "operation", "transfer",
                "approvalId", transferApprovalDecision.approvalId()));
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new FlowResultResponseDto(false, transferApprovalDecision.reason(), sourceWallet.getId(),
                    UUID.fromString(transferApprovalDecision.approvalId()), "wallet.transfer.pending_approval"));
        }

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
            deviceLocationRiskService.markTrusted(userId, transferDeviceId, transferLocationHint);
            AUDIT_LOG.info("wallet_transfer_success userId={} sourceWalletId={} targetWalletId={} amount={} walletId={} transactionId={}",
                userId, sourceWallet.getId(), request.getTargetWalletId(), request.getAmount(),
                sourceWallet.getId(), result.transactionId());
            apiAbuseProtectionService.recordSuccess(userId, sourceIp, deviceId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } else {
            apiAbuseProtectionService.recordViolation(userId, sourceIp, deviceId);
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

        ResponseEntity<FlowResultResponseDto> emergencyBlocked = blockIfEmergencyControlTriggered(wallet.getId(), "wallet.etc.failed");
        if (emergencyBlocked != null) {
            return emergencyBlocked;
        }

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
        String sourceIp = resolveClientIp(httpRequest);
        ApiAbuseProtectionService.AbuseDecision abuseDecision = apiAbuseProtectionService.evaluate(userId, sourceIp, deviceId);
        if (!abuseDecision.allowed()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new FlowResultResponseDto(false, abuseDecision.reason(), wallet.getId(), null, "wallet.etc.failed"));
        }
        WalletOperationRateLimitService.RateLimitResult etcRateLimit = operationRateLimitService.enforce(
            WalletOperationRateLimitService.Operation.ETC_REDEMPTION,
            userId,
            sourceIp,
            deviceId,
            code);
        if (!etcRateLimit.allowed()) {
            apiAbuseProtectionService.recordViolation(userId, sourceIp, deviceId);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new FlowResultResponseDto(false, "Withdrawal-code redemption rate limit exceeded", wallet.getId(), null, "wallet.etc.failed"));
        }

        WalletFlowResult result = walletService.redeemEtc(code, request.getIdempotencyKey(), deviceId, sourceIp);
        FlowResultResponseDto response = walletMapper.toFlowResultResponseDto(result);

        if (result.success()) {
            apiAbuseProtectionService.recordSuccess(userId, sourceIp, deviceId);
            AUDIT_LOG.info("wallet_etc_redeemed userId={} code={} walletId={}",
                userId, code, wallet.getId());
            return ResponseEntity.ok(response);
        } else {
            apiAbuseProtectionService.recordViolation(userId, sourceIp, deviceId);
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

        LimitsResponseDto limits = walletLimitEnforcementService.getLimits(wallet.getId());

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

        Reservation reservation = findOwnedReservation(id, userId);

        WalletFlowResult result = walletService.releaseReservation(id, idempotencyKey);
        FlowResultResponseDto response = walletMapper.toFlowResultResponseDto(result);

        if (result.success()) {
            AUDIT_LOG.info("wallet_reservation_released userId={} reservationId={} walletId={}",
                userId, id, reservation.getWallet().getId());
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

        Reservation reservation = findOwnedReservation(id, userId);

        WalletFlowResult result = walletService.confirmReservation(id, idempotencyKey);
        FlowResultResponseDto response = walletMapper.toFlowResultResponseDto(result);

        if (result.success()) {
            AUDIT_LOG.info("wallet_reservation_confirmed userId={} reservationId={} walletId={}",
                userId, id, reservation.getWallet().getId());
            return ResponseEntity.ok(response);
        } else {
            AUDIT_LOG.warn("wallet_reservation_confirm_failed userId={} reservationId={} reason={}",
                userId, id, result.message());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    private Reservation findOwnedReservation(UUID reservationId, UUID userId) {
        Reservation reservation = reservationRepository.findByIdAndWalletUserId(reservationId, userId)
            .orElseThrow(() -> new WalletNotFoundException("Reservation not found: " + reservationId));

        return reservation;
    }

    private String resolveLocationHint(HttpServletRequest request) {
        String headerLocation = request.getHeader("X-Geo-Region");
        if (headerLocation != null && !headerLocation.isBlank()) {
            return headerLocation.trim();
        }

        String sourceIp = request.getRemoteAddr();
        if (sourceIp == null || sourceIp.isBlank()) {
            return "unknown";
        }

        String[] blocks = sourceIp.split("\\.");
        if (blocks.length >= 2) {
            return blocks[0] + "." + blocks[1];
        }
        return sourceIp;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader(FORWARDED_FOR_HEADER);
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String[] addresses = forwardedFor.split(",");
            if (addresses.length > 0 && !addresses[0].isBlank()) {
                return addresses[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

    private Long parseLongHeader(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private ResponseEntity<FlowResultResponseDto> blockIfEmergencyControlTriggered(UUID walletId, String eventType) {
        if (emergencyControlService.isGlobalKillSwitchEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new FlowResultResponseDto(false, emergencyControlService.globalKillSwitchReason(), walletId, null, eventType));
        }
        if (walletId != null && emergencyControlService.isWalletEmergencyFrozen(walletId)) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new FlowResultResponseDto(false, emergencyControlService.walletEmergencyReason(walletId), walletId, null, eventType));
        }
        return null;
    }

    private void createAmlCase(String category,
                               UUID userId,
                               UUID walletId,
                               String operation,
                               String reason,
                               java.util.Map<String, Object> evidence) {
        try {
            amlCaseWorkflowService.createCase(category, userId, walletId, operation, reason, evidence);
            walletMetricsRecorder.recordAmlCase("opened", category);
        } catch (RuntimeException ex) {
            LOGGER.warn("aml_case_creation_failed category={} userId={} walletId={} operation={} reason={}",
                category, userId, walletId, operation, ex.getMessage());
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

        if (authentication.getPrincipal() instanceof UserJwtPrincipal principal) {
            return principal.userId();
        }

        throw new UnauthorizedException("Invalid authenticated user context");
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
