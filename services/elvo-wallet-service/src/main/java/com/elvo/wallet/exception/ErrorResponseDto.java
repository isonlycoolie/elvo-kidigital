package com.elvo.wallet.exception;

public class ErrorResponseDto {

    private String code;
    private String message;

    public ErrorResponseDto() {
    }

    public ErrorResponseDto(String code, String message) {
        this.code = code;
        this.message = message;
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
}
