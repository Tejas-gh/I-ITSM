package com.iitsm.domain.exception;

public class ServiceNotFoundException extends ItsmException {
    public ServiceNotFoundException(String message) {
        super("SERVICE_NOT_FOUND", message);
    }
}
