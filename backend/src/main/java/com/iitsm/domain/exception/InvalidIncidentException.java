package com.iitsm.domain.exception;

public class InvalidIncidentException extends ItsmException {
    public InvalidIncidentException(String message) {
        super("INVALID_INCIDENT", message);
    }
}
