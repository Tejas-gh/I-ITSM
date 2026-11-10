package com.iitsm.domain.exception;

public class UnauthorizedOperationException extends ItsmException {
    public UnauthorizedOperationException(String message) {
        super("UNAUTHORIZED_OPERATION", message);
    }
}
