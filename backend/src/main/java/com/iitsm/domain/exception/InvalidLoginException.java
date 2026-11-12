package com.iitsm.domain.exception;

public class InvalidLoginException extends ItsmException {
    public InvalidLoginException(String message) {
        super("INVALID_LOGIN", message);
    }
}
