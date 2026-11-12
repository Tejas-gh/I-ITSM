package com.iitsm.application.command;

import com.iitsm.domain.exception.InvalidInputException;

public record LoginCommand(String userId, String password) {

    public LoginCommand {
        if (userId == null || userId.isBlank()) {
            throw new InvalidInputException("userId must not be blank");
        }
        if (password == null || password.isEmpty()) {
            throw new InvalidInputException("password must not be blank");
        }
    }

    @Override
    public String toString() {
        return "LoginCommand[userId=" + userId + "]";
    }
}
