package com.iitsm.domain.exception;

public class MissingSlaException extends ItsmException {
    public MissingSlaException(String message) {
        super("MISSING_SLA", message);
    }
}
