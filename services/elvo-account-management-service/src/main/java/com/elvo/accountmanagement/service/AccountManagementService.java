package com.elvo.accountmanagement.service;

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

public interface AccountManagementService {

    AccountResponse createAccount(CreateAccountRequest request);

    AccountResponse getAccountById(java.util.UUID accountId);

    AccountResponse getAccountByUserId(java.util.UUID userId);

    AccountResponse getAccountByEan(String ean);

    ValidationResponse validateTransfer(ValidationRequest request);

    ValidationResponse validateWithdrawal(ValidationRequest request);

    ValidationResponse validateReceive(ValidationRequest request);

    LimitResponse checkLimit(LimitCheckRequest request);

    LimitChangeWorkflowResponse requestLimitChange(LimitChangeRequest request);

    LimitChangeWorkflowResponse activateLimitChange(LimitChangeActivationRequest request);

    PermissionResponse checkPermission(PermissionCheckRequest request);

    PermissionChangeWorkflowResponse requestPermissionChange(PermissionChangeRequest request);

    PermissionChangeWorkflowResponse approvePermissionChange(PermissionChangeApprovalRequest request);

    AccountResponse activateAccount(LifecycleRequest request);

    AccountResponse syncPostVerification(com.elvo.accountmanagement.contract.AccountContracts.VerificationSyncRequest request);

    AccountResponse freezeAccount(LifecycleRequest request);

    AccountResponse unfreezeAccount(LifecycleRequest request);

    AccountResponse suspendAccount(LifecycleRequest request);

    AccountResponse closeAccount(LifecycleRequest request);

    AccountResponse reopenAccount(LifecycleRequest request);

    AccountResponse archiveAccount(LifecycleRequest request);

    AccountResponse restrictAccount(RestrictionRequest request);

    AccountResponse removeRestriction(RestrictionRequest request);

    RestrictionResponse createRestrictionRecord(RestrictionRequest request);

    RelationshipUnlinkResponse unlinkRelationship(RelationshipUnlinkRequest request);

    AdminActionWorkflowResponse requestAdminAction(AdminActionRequest request);

    AdminActionWorkflowResponse approveAdminAction(AdminActionApprovalRequest request);
}
