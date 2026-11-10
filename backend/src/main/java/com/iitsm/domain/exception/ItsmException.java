package com.iitsm.domain.exception;

public class ItsmException extends RuntimeException {
    private final String code;

    protected ItsmException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
