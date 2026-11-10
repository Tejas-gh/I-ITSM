package com.iitsm.domain.exception;

public class TechnicianOverloadedException extends ItsmException {
    public TechnicianOverloadedException(String message) {
        super("TECHNICIAN_OVERLOADED", message);
    }
}
