package com.iitsm.api.dto;

import java.time.Instant;

public record AuthResponse(String accessToken, String tokenType, Instant expiresAt) {

    @Override
    public String toString() {
        return "AuthResponse[tokenType=" + tokenType + ", expiresAt=" + expiresAt + "]";
    }
}
