package com.elvo.identity.exception;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.elvo.identity.monitoring.SentryExceptionReporter;
import com.elvo.identity.security.SensitiveDataMasker;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("audit.identity.controller");
    private final SentryExceptionReporter sentryExceptionReporter;

    public GlobalExceptionHandler(SentryExceptionReporter sentryExceptionReporter) {
        this.sentryExceptionReporter = sentryExceptionReporter;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        AUDIT_LOG.warn("validation_error message={}", SensitiveDataMasker.maskText(ex.getMessage()));
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("errors", ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .collect(Collectors.toMap(
                fieldError -> fieldError.getField(),
                fieldError -> fieldError.getDefaultMessage() == null ? "Invalid value" : fieldError.getDefaultMessage(),
                (existing, replacement) -> replacement,
                LinkedHashMap::new
            )));

        return ResponseEntity.badRequest()
            .body(ApiResponse.error("VALIDATION_ERROR", "Request validation failed", details));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraint(ConstraintViolationException ex) {
        AUDIT_LOG.warn("constraint_violation message={}", SensitiveDataMasker.maskText(ex.getMessage()));
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("violations", ex.getConstraintViolations()
            .stream()
            .map(violation -> Map.of(
                "field", violation.getPropertyPath().toString(),
                "message", violation.getMessage()))
            .toList());

        return ResponseEntity.badRequest()
            .body(ApiResponse.error("CONSTRAINT_VIOLATION", "Constraint validation failed", details));
        }

        @ExceptionHandler(VerificationRequiredException.class)
        public ResponseEntity<ApiResponse<Void>> handleVerificationRequired(VerificationRequiredException ex) {
            AUDIT_LOG.warn("verification_required message={}", SensitiveDataMasker.maskText(ex.getMessage()));
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("VERIFICATION_REQUIRED", SensitiveDataMasker.maskText(ex.getMessage())));
        }

        @ExceptionHandler(AuthenticationException.class)
        public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
            String masked = SensitiveDataMasker.maskText(ex.getMessage());
            AUDIT_LOG.warn("authentication_failed message={}", masked);
            sentryExceptionReporter.captureCriticalException(ex, request, Map.of("exceptionType", "AUTHENTICATION_FAILED"));
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ApiResponse.error("AUTHENTICATION_FAILED", "Authentication failed"));
        }

        @ExceptionHandler(AccessDeniedException.class)
            public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
            AUDIT_LOG.warn("access_denied message={}", SensitiveDataMasker.maskText(ex.getMessage()));
            sentryExceptionReporter.captureCriticalException(ex, request, Map.of("exceptionType", "ACCESS_DENIED"));
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ApiResponse.error("ACCESS_DENIED", "Access is denied"));
    }

    @ExceptionHandler(Exception.class)
        public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception ex, HttpServletRequest request) {
            LOGGER.error("Unexpected error occurred", ex);
            AUDIT_LOG.error("unexpected_error message={}", SensitiveDataMasker.maskText(ex.getMessage()));
            sentryExceptionReporter.captureCriticalException(ex, request, Map.of("errorContext", "INTERNAL_ERROR"));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error("INTERNAL_ERROR", "Unexpected server error"));
    }
}
