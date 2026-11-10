package com.iitsm.domain.exception;

public class TechnicianUnavailableException extends ItsmException {
    public TechnicianUnavailableException(String message) {
        super("TECHNICIAN_UNAVAILABLE", message);
    }
}
