package com.iitsm.domain.exception;

public class IncidentNotFoundException extends ItsmException {
    public IncidentNotFoundException(String message) {
        super("INCIDENT_NOT_FOUND", message);
    }
}
