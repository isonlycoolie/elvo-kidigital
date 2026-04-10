package com.elvo.accountmanagement.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.elvo.accountmanagement.contract.AccountContracts.ApiResponse;
import com.elvo.accountmanagement.contract.AccountContracts.AccountResponse;
import com.elvo.accountmanagement.contract.AccountContracts.AdminActionApprovalRequest;
import com.elvo.accountmanagement.contract.AccountContracts.AdminActionRequest;
import com.elvo.accountmanagement.contract.AccountContracts.AdminActionWorkflowResponse;
import com.elvo.accountmanagement.contract.AccountContracts.CreateAccountRequest;
import com.elvo.accountmanagement.contract.AccountContracts.LifecycleRequest;
import com.elvo.accountmanagement.contract.AccountContracts.LimitChangeActivationRequest;
import com.elvo.accountmanagement.contract.AccountContracts.LimitChangeRequest;
import com.elvo.accountmanagement.contract.AccountContracts.LimitChangeWorkflowResponse;
import com.elvo.accountmanagement.contract.AccountContracts.LimitCheckRequest;
import com.elvo.accountmanagement.contract.AccountContracts.LimitResponse;
import com.elvo.accountmanagement.contract.AccountContracts.PermissionCheckRequest;
import com.elvo.accountmanagement.contract.AccountContracts.PermissionChangeApprovalRequest;
import com.elvo.accountmanagement.contract.AccountContracts.PermissionChangeRequest;
import com.elvo.accountmanagement.contract.AccountContracts.PermissionChangeWorkflowResponse;
import com.elvo.accountmanagement.contract.AccountContracts.PermissionResponse;
import com.elvo.accountmanagement.contract.AccountContracts.RestrictionRequest;
import com.elvo.accountmanagement.contract.AccountContracts.RestrictionResponse;
import com.elvo.accountmanagement.contract.AccountContracts.RelationshipUnlinkRequest;
import com.elvo.accountmanagement.contract.AccountContracts.RelationshipUnlinkResponse;
import com.elvo.accountmanagement.contract.AccountContracts.ValidationRequest;
import com.elvo.accountmanagement.contract.AccountContracts.ValidationResponse;
import com.elvo.accountmanagement.service.AccountManagementService;

@RestController
@RequestMapping("/api/v1/internal/accounts")
@Validated
public class InternalAccountController {

    private final AccountManagementService accountManagementService;

    public InternalAccountController(AccountManagementService accountManagementService) {
        this.accountManagementService = accountManagementService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AccountResponse>> createAccount(@RequestBody CreateAccountRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Account created", accountManagementService.createAccount(request)));
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<ApiResponse<AccountResponse>> getByAccountId(@PathVariable UUID accountId) {
        return ResponseEntity.ok(ApiResponse.ok("Account loaded", accountManagementService.getAccountById(accountId)));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<AccountResponse>> getByUserId(@PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok("Account loaded", accountManagementService.getAccountByUserId(userId)));
    }

    @GetMapping("/ean/{ean}")
    public ResponseEntity<ApiResponse<AccountResponse>> getByEan(@PathVariable String ean) {
        return ResponseEntity.ok(ApiResponse.ok("Account loaded", accountManagementService.getAccountByEan(ean)));
    }

    @PostMapping("/validate-transfer")
    public ResponseEntity<ApiResponse<ValidationResponse>> validateTransfer(@RequestBody ValidationRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Transfer validation completed", accountManagementService.validateTransfer(request)));
    }

    @PostMapping("/validate-withdrawal")
    public ResponseEntity<ApiResponse<ValidationResponse>> validateWithdrawal(@RequestBody ValidationRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Withdrawal validation completed", accountManagementService.validateWithdrawal(request)));
    }

    @PostMapping("/validate-receive")
    public ResponseEntity<ApiResponse<ValidationResponse>> validateReceive(@RequestBody ValidationRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Receive validation completed", accountManagementService.validateReceive(request)));
    }

    @PostMapping("/check-limit")
    public ResponseEntity<ApiResponse<LimitResponse>> checkLimit(@RequestBody LimitCheckRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Limit check completed", accountManagementService.checkLimit(request)));
    }

    @PostMapping("/limit-change/request")
    public ResponseEntity<ApiResponse<LimitChangeWorkflowResponse>> requestLimitChange(@RequestBody LimitChangeRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Limit change request submitted", accountManagementService.requestLimitChange(request)));
    }

    @PostMapping("/limit-change/activate")
    public ResponseEntity<ApiResponse<LimitChangeWorkflowResponse>> activateLimitChange(@RequestBody LimitChangeActivationRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Limit change activation processed", accountManagementService.activateLimitChange(request)));
    }

    @PostMapping("/check-permission")
    public ResponseEntity<ApiResponse<PermissionResponse>> checkPermission(@RequestBody PermissionCheckRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Permission check completed", accountManagementService.checkPermission(request)));
    }

    @PostMapping("/permission-change/request")
    public ResponseEntity<ApiResponse<PermissionChangeWorkflowResponse>> requestPermissionChange(@RequestBody PermissionChangeRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Permission change request submitted", accountManagementService.requestPermissionChange(request)));
    }

    @PostMapping("/permission-change/approve")
    public ResponseEntity<ApiResponse<PermissionChangeWorkflowResponse>> approvePermissionChange(@RequestBody PermissionChangeApprovalRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Permission change request approved", accountManagementService.approvePermissionChange(request)));
    }

    @PostMapping("/activate")
    public ResponseEntity<ApiResponse<AccountResponse>> activate(@RequestBody LifecycleRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Account activated", accountManagementService.activateAccount(request)));
    }

    @PostMapping("/freeze")
    public ResponseEntity<ApiResponse<AccountResponse>> freeze(@RequestBody LifecycleRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Account frozen", accountManagementService.freezeAccount(request)));
    }

    @PostMapping("/unfreeze")
    public ResponseEntity<ApiResponse<AccountResponse>> unfreeze(@RequestBody LifecycleRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Account unfrozen", accountManagementService.unfreezeAccount(request)));
    }

    @PostMapping("/suspend")
    public ResponseEntity<ApiResponse<AccountResponse>> suspend(@RequestBody LifecycleRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Account suspended", accountManagementService.suspendAccount(request)));
    }

    @PostMapping("/close")
    public ResponseEntity<ApiResponse<AccountResponse>> close(@RequestBody LifecycleRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Account closed", accountManagementService.closeAccount(request)));
    }

    @PostMapping("/reopen")
    public ResponseEntity<ApiResponse<AccountResponse>> reopen(@RequestBody LifecycleRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Account reopened", accountManagementService.reopenAccount(request)));
    }

    @PostMapping("/archive")
    public ResponseEntity<ApiResponse<AccountResponse>> archive(@RequestBody LifecycleRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Account archived", accountManagementService.archiveAccount(request)));
    }

    @PostMapping("/restrict")
    public ResponseEntity<ApiResponse<RestrictionResponse>> restrict(@RequestBody RestrictionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Restriction recorded", accountManagementService.createRestrictionRecord(request)));
    }

    @PostMapping("/remove-restriction")
    public ResponseEntity<ApiResponse<AccountResponse>> removeRestriction(@RequestBody RestrictionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Restriction removed", accountManagementService.removeRestriction(request)));
    }

    @PostMapping("/admin-actions/request")
    public ResponseEntity<ApiResponse<AdminActionWorkflowResponse>> requestAdminAction(@RequestBody AdminActionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Admin action request submitted", accountManagementService.requestAdminAction(request)));
    }

    @PostMapping("/admin-actions/approve")
    public ResponseEntity<ApiResponse<AdminActionWorkflowResponse>> approveAdminAction(@RequestBody AdminActionApprovalRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Admin action approved", accountManagementService.approveAdminAction(request)));
    }

    @PostMapping("/relationships/unlink")
    public ResponseEntity<ApiResponse<RelationshipUnlinkResponse>> unlinkRelationship(@RequestBody RelationshipUnlinkRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Relationship unlinked", accountManagementService.unlinkRelationship(request)));
    }
}
