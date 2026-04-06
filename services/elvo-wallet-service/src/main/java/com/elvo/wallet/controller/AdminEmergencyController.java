package com.elvo.wallet.controller;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.elvo.wallet.dto.request.FreezeUnfreezeRequestDto;
import com.elvo.wallet.security.EmergencyControlService;
import com.elvo.wallet.service.WalletService;
import com.elvo.wallet.service.model.WalletFlowResult;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/admin/emergency")
@Validated
@PreAuthorize("hasRole('OPERATIONS_ADMIN')")
public class AdminEmergencyController {

    private final EmergencyControlService emergencyControlService;
    private final WalletService walletService;

    public AdminEmergencyController(EmergencyControlService emergencyControlService, WalletService walletService) {
        this.emergencyControlService = emergencyControlService;
        this.walletService = walletService;
    }

    @PostMapping("/kill-switch/enable")
    public ResponseEntity<Map<String, Object>> enableKillSwitch(@Valid @RequestBody FreezeUnfreezeRequestDto request) {
        String reason = request.getReason();
        emergencyControlService.setGlobalKillSwitch(true, reason);
        return ResponseEntity.accepted().body(Map.of(
                "enabled", true,
                "reason", reason == null ? "Emergency control triggered by operator" : reason));
    }

    @PostMapping("/kill-switch/disable")
    public ResponseEntity<Map<String, Object>> disableKillSwitch(@Valid @RequestBody FreezeUnfreezeRequestDto request) {
        emergencyControlService.setGlobalKillSwitch(false, request.getReason());
        return ResponseEntity.ok(Map.of("enabled", false));
    }

    @PostMapping("/wallets/{walletId}/freeze")
    public ResponseEntity<WalletFlowResult> emergencyFreezeWallet(@PathVariable UUID walletId,
                                                                  @Valid @RequestBody FreezeUnfreezeRequestDto request) {
        emergencyControlService.freezeWalletEmergency(walletId, request.getReason());
        WalletFlowResult result = walletService.freezeWallet(walletId, request.getReason());
        return ResponseEntity.accepted().body(result);
    }

    @PostMapping("/wallets/{walletId}/unfreeze")
    public ResponseEntity<WalletFlowResult> emergencyUnfreezeWallet(@PathVariable UUID walletId,
                                                                    @Valid @RequestBody FreezeUnfreezeRequestDto request) {
        emergencyControlService.unfreezeWalletEmergency(walletId);
        WalletFlowResult result = walletService.unfreezeWallet(walletId, request.getReason());
        return ResponseEntity.ok(result);
    }
}
