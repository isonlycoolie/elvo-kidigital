package com.elvo.identity.exception;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.elvo.identity.monitoring.SentryExceptionReporter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final SentryExceptionReporter sentryExceptionReporter;

    public GlobalExceptionHandler(SentryExceptionReporter sentryExceptionReporter) {
        this.sentryExceptionReporter = sentryExceptionReporter;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
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

        @ExceptionHandler(AuthenticationException.class)
        public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
            sentryExceptionReporter.captureCriticalException(ex, request, Map.of("exceptionType", ex.getClass().getSimpleName()));
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ApiResponse.error("AUTHENTICATION_FAILED", "Authentication failed"));
        }

        @ExceptionHandler(AccessDeniedException.class)
            public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
            sentryExceptionReporter.captureCriticalException(ex, request, Map.of("exceptionType", ex.getClass().getSimpleName()));
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ApiResponse.error("ACCESS_DENIED", "Access is denied"));
    }

    @ExceptionHandler(Exception.class)
        public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception ex, HttpServletRequest request) {
            sentryExceptionReporter.captureCriticalException(ex, request, Map.of("exceptionType", ex.getClass().getSimpleName()));
        Map<String, Object> details = Map.of("exception", ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error("INTERNAL_ERROR", "Unexpected server error", details));
    }
}
