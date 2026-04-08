package com.elvo.wallet.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.elvo.wallet.controller.WalletController;
import com.elvo.wallet.monitoring.SentryExceptionReporter;
import com.elvo.wallet.security.SensitiveDataMasker;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("audit.wallet.controller");
    private final SentryExceptionReporter sentryExceptionReporter;

    public GlobalExceptionHandler(@Nullable SentryExceptionReporter sentryExceptionReporter) {
        this.sentryExceptionReporter = sentryExceptionReporter;
    }

    @ExceptionHandler(WalletController.WalletNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleWalletNotFound(WalletController.WalletNotFoundException ex) {
        String masked = SensitiveDataMasker.maskText(ex.getMessage());
        AUDIT_LOG.warn("wallet_not_found exception={}", masked);
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error("WALLET_NOT_FOUND", masked));
    }

    @ExceptionHandler(WalletController.UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorized(WalletController.UnauthorizedException ex) {
        String masked = SensitiveDataMasker.maskText(ex.getMessage());
        AUDIT_LOG.warn("unauthorized_access exception={}", masked);
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(ApiResponse.error("UNAUTHORIZED", masked));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        AUDIT_LOG.warn("access_denied exception={}", SensitiveDataMasker.maskText(ex.getMessage()));
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(ApiResponse.error("ACCESS_DENIED", "You do not have permission to perform this action"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ValidationErrorResponseDto> handleValidation(MethodArgumentNotValidException ex,
                                                                           HttpServletRequest request) {
        AUDIT_LOG.warn("validation_error exception={}", SensitiveDataMasker.maskText(ex.getMessage()));
            captureIfPresent(ex, request, java.util.Map.of("category", "validation"));

        ValidationErrorResponseDto errorResponse = new ValidationErrorResponseDto();
        errorResponse.setCode("VALIDATION_ERROR");
        errorResponse.setMessage("Request validation failed");

        ex.getBindingResult().getFieldErrors().forEach(fieldError ->
            errorResponse.addFieldError(fieldError.getField(), fieldError.getDefaultMessage()));
        ex.getBindingResult().getGlobalErrors().forEach(globalError ->
            errorResponse.addGlobalError(globalError.getDefaultMessage()));

        return ResponseEntity.badRequest().body(errorResponse);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraint(ConstraintViolationException ex,
                                                              HttpServletRequest request) {
        String masked = SensitiveDataMasker.maskText(ex.getMessage());
        AUDIT_LOG.warn("constraint_violation exception={}", masked);
        captureIfPresent(ex, request, java.util.Map.of("category", "constraint"));
        return ResponseEntity.badRequest()
            .body(ApiResponse.error("CONSTRAINT_VIOLATION", masked));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLockFailure(ObjectOptimisticLockingFailureException ex,
                                                                         HttpServletRequest request) {
        AUDIT_LOG.warn("optimistic_lock_conflict exception={}", SensitiveDataMasker.maskText(ex.getMessage()));
        captureIfPresent(ex, request, java.util.Map.of("category", "optimistic-lock"));
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("CONCURRENCY_CONFLICT", "Wallet update conflict detected, please retry"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception ex, HttpServletRequest request) {
        LOGGER.error("Unexpected error occurred", ex);
        AUDIT_LOG.error("unexpected_error message={}", SensitiveDataMasker.maskText(ex.getMessage()));
        captureIfPresent(ex, request, java.util.Map.of("category", "unhandled"));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "Unexpected server error"));
    }

    private void captureIfPresent(Throwable ex, HttpServletRequest request, java.util.Map<String, Object> extraContext) {
        if (sentryExceptionReporter != null) {
            sentryExceptionReporter.captureCriticalException(ex, request, extraContext);
        }
    }
}
