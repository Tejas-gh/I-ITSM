package com.iitsm.infrastructure.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT settings ({@code iitsm.security.jwt.*}). The signing secret has no default and must come from the
 * environment ({@code JWT_SECRET}); {@code expiration} defaults to one hour.
 */
@ConfigurationProperties(prefix = "iitsm.security.jwt")
public record JwtProperties(String secret, Duration expiration) {

    public JwtProperties {
        if (expiration == null) {
            expiration = Duration.ofHours(1);
        }
    }

    @Override
    public String toString() {
        return "JwtProperties[expiration=" + expiration + "]";
    }
}
