package com.iitsm.domain.exception;

public class DuplicateIncidentException extends ItsmException {
    public DuplicateIncidentException(String message) {
        super("DUPLICATE_INCIDENT", message);
    }
}
