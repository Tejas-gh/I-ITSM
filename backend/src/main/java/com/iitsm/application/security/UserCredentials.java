package com.iitsm.application.security;

import com.iitsm.domain.exception.InvalidInputException;

/**
 * The stored login secret of one user: the BCrypt hash of the password, never the password itself.
 * Kept apart from {@code User} so the domain model carries no credentials.
 */
public record UserCredentials(String userId, String passwordHash) {

    public UserCredentials {
        if (userId == null || userId.isBlank()) {
            throw new InvalidInputException("userId must not be blank");
        }
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new InvalidInputException("passwordHash must not be blank");
        }
    }

    @Override
    public String toString() {
        return "UserCredentials[userId=" + userId + "]";
    }
}
