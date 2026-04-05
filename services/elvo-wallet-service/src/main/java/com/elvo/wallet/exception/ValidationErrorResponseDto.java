package com.elvo.wallet.exception;

import java.util.HashMap;
import java.util.Map;

public class ValidationErrorResponseDto {

    private String code;
    private String message;
    private Map<String, String> errors = new HashMap<>();

    public ValidationErrorResponseDto() {
    }

    public void addFieldError(String field, String message) {
        this.errors.put(field, message);
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Map<String, String> getErrors() {
        return errors;
    }

    public void setErrors(Map<String, String> errors) {
        this.errors = errors;
    }
}
