package com.elvo.wallet.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.elvo.wallet.controller.WalletController;

import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("audit.wallet.controller");

    @ExceptionHandler(WalletController.WalletNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleWalletNotFound(WalletController.WalletNotFoundException ex) {
        AUDIT_LOG.warn("wallet_not_found exception={}", ex.getMessage());
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error("WALLET_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(WalletController.UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorized(WalletController.UnauthorizedException ex) {
        AUDIT_LOG.warn("unauthorized_access exception={}", ex.getMessage());
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(ApiResponse.error("UNAUTHORIZED", ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        AUDIT_LOG.warn("access_denied exception={}", ex.getMessage());
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(ApiResponse.error("ACCESS_DENIED", "You do not have permission to perform this action"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ValidationErrorResponseDto> handleValidation(MethodArgumentNotValidException ex) {
        AUDIT_LOG.warn("validation_error exception={}", ex.getMessage());

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
    public ResponseEntity<ApiResponse<Void>> handleConstraint(ConstraintViolationException ex) {
        AUDIT_LOG.warn("constraint_violation exception={}", ex.getMessage());
        return ResponseEntity.badRequest()
            .body(ApiResponse.error("CONSTRAINT_VIOLATION", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception ex) {
        LOGGER.error("Unexpected error occurred", ex);
        AUDIT_LOG.error("unexpected_error message={}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "Unexpected server error"));
    }
}
