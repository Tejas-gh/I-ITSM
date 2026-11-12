package com.iitsm.application.security;

import java.time.Instant;

import com.iitsm.domain.exception.InvalidInputException;

/** A signed access token and the instant it stops being valid. */
public record AccessToken(String value, Instant expiresAt) {

    public AccessToken {
        if (value == null || value.isBlank()) {
            throw new InvalidInputException("token value must not be blank");
        }
        if (expiresAt == null) {
            throw new InvalidInputException("expiresAt must not be null");
        }
    }

    @Override
    public String toString() {
        return "AccessToken[expiresAt=" + expiresAt + "]";
    }
}
