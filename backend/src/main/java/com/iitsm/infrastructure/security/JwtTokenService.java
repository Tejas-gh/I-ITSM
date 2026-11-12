package com.iitsm.infrastructure.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import com.iitsm.application.security.AccessToken;
import com.iitsm.application.security.TokenIssuer;
import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.model.User;

/**
 * Issues HS256-signed JWTs. A token carries only the user id ({@code sub}), the role ({@code role}),
 * and the issue/expiry times; never a password or hash.
 */
public final class JwtTokenService implements TokenIssuer {

    /** Name of the claim holding the user's {@code Role} name. */
    public static final String ROLE_CLAIM = "role";

    private final JwtEncoder encoder;
    private final Duration lifetime;
    private final Clock clock;

    public JwtTokenService(JwtEncoder encoder, Duration lifetime, Clock clock) {
        if (encoder == null || lifetime == null || clock == null) {
            throw new InvalidInputException("encoder, lifetime and clock must not be null");
        }
        if (lifetime.isZero() || lifetime.isNegative()) {
            throw new InvalidInputException("token lifetime must be positive");
        }
        this.encoder = encoder;
        this.lifetime = lifetime;
        this.clock = clock;
    }

    @Override
    public AccessToken issue(User user) {
        if (user == null) {
            throw new InvalidInputException("user must not be null");
        }
        Instant issuedAt = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.getId())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(lifetime))
                .claim(ROLE_CLAIM, user.getRole().name())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        Jwt jwt = encoder.encode(JwtEncoderParameters.from(header, claims));
        return new AccessToken(jwt.getTokenValue(), jwt.getExpiresAt());
    }
}
