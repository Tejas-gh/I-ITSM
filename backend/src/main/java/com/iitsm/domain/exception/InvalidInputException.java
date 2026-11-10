package com.iitsm.domain.exception;

public class InvalidInputException extends ItsmException {
    public InvalidInputException(String message) {
        super("INVALID_INPUT", message);
    }
}
