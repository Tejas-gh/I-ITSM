package com.iitsm.domain.exception;

public class UserNotFoundException extends ItsmException {
    public UserNotFoundException(String message) {
        super("USER_NOT_FOUND", message);
    }
}
