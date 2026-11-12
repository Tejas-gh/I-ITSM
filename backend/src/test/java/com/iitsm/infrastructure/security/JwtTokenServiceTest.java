package com.iitsm.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.iitsm.application.security.AccessToken;
import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.model.Administrator;
import com.iitsm.domain.model.Employee;
import com.iitsm.domain.model.Role;
import com.iitsm.domain.model.Technician;
import com.iitsm.domain.model.User;

class JwtTokenServiceTest {

    private static final Duration LIFETIME = Duration.ofHours(1);

    private static final SecretKey KEY = key("unit-test-signing-secret-0123456789-abcdef");

    private final JwtEncoder encoder = NimbusJwtEncoder.withSecretKey(KEY).algorithm(MacAlgorithm.HS256).build();
    private final JwtDecoder decoder = NimbusJwtDecoder.withSecretKey(KEY).macAlgorithm(MacAlgorithm.HS256).build();
    private final JwtTokenService service = new JwtTokenService(encoder, LIFETIME, Clock.systemUTC());

    private static SecretKey key(String secret) {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    private static Employee employee() {
        return new Employee("E100", "Alice", "alice@corp.com", "Finance");
    }

    @Test
    void issue_validUser_producesATokenTheDecoderAccepts() {
        AccessToken token = service.issue(employee());

        Jwt jwt = decoder.decode(token.value());

        assertEquals("E100", jwt.getSubject());
        assertEquals("EMPLOYEE", jwt.getClaimAsString(JwtTokenService.ROLE_CLAIM));
        assertNotNull(jwt.getIssuedAt());
        assertEquals(LIFETIME, Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()));
        assertEquals(jwt.getExpiresAt(), token.expiresAt().truncatedTo(ChronoUnit.SECONDS));
    }

    @Test
    void issue_everyRole_putsThatRoleNameInTheRoleClaim() {
        Map<Role, User> usersByRole = Map.of(
                Role.EMPLOYEE, employee(),
                Role.TECHNICIAN, new Technician("T1", "Tom", "tom@corp.com", Role.TECHNICIAN, Map.of(), 1, 3),
                Role.TEAM_LEAD, new Technician("L1", "Lea", "lea@corp.com", Role.TEAM_LEAD, Map.of(), 5, 3),
                Role.ADMINISTRATOR, new Administrator("A1", "Ann", "ann@corp.com", Role.ADMINISTRATOR),
                Role.IT_MANAGER, new Administrator("M1", "Max", "max@corp.com", Role.IT_MANAGER));

        for (Role role : Role.values()) {
            Jwt jwt = decoder.decode(service.issue(usersByRole.get(role)).value());
            assertEquals(role.name(), jwt.getClaimAsString(JwtTokenService.ROLE_CLAIM));
        }
    }

    @Test
    void issue_claims_containNoPasswordOrPersonalData() {
        Jwt jwt = decoder.decode(service.issue(employee()).value());

        List<String> forbidden = List.of("password", "passwordHash", "password_hash", "email", "name", "department");
        for (String claim : forbidden) {
            assertFalse(jwt.getClaims().containsKey(claim), "token must not carry claim " + claim);
        }
        assertTrue(jwt.getClaims().keySet().containsAll(List.of("sub", "role", "iat", "exp")));
    }

    @Test
    void decode_expiredToken_isRejected() {
        Clock longAgo = Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC);
        String expired = new JwtTokenService(encoder, LIFETIME, longAgo).issue(employee()).value();

        assertThrows(JwtException.class, () -> decoder.decode(expired));
    }

    @Test
    void decode_tokenWithTamperedPayload_isRejected() {
        String[] parts = service.issue(employee()).value().split("\\.");
        String forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"E100\",\"role\":\"ADMINISTRATOR\",\"exp\":4102444800}".getBytes(StandardCharsets.UTF_8));
        String forged = parts[0] + "." + forgedPayload + "." + parts[2];

        assertThrows(JwtException.class, () -> decoder.decode(forged));
    }

    @Test
    void decode_tokenSignedWithAnotherKey_isRejected() {
        JwtEncoder otherEncoder = NimbusJwtEncoder
                .withSecretKey(key("a-completely-different-secret-key-0123456789"))
                .algorithm(MacAlgorithm.HS256).build();
        String foreign = new JwtTokenService(otherEncoder, LIFETIME, Clock.systemUTC()).issue(employee()).value();

        assertThrows(JwtException.class, () -> decoder.decode(foreign));
    }

    @Test
    void decode_garbage_isRejected() {
        assertThrows(JwtException.class, () -> decoder.decode("not.a.jwt"));
    }

    @Test
    void issue_nullUser_throwsInvalidInputException() {
        assertThrows(InvalidInputException.class, () -> service.issue(null));
    }

    @Test
    void constructor_invalidArguments_throwInvalidInputException() {
        assertThrows(InvalidInputException.class, () -> new JwtTokenService(null, LIFETIME, Clock.systemUTC()));
        assertThrows(InvalidInputException.class, () -> new JwtTokenService(encoder, null, Clock.systemUTC()));
        assertThrows(InvalidInputException.class, () -> new JwtTokenService(encoder, LIFETIME, null));
        assertThrows(InvalidInputException.class,
                () -> new JwtTokenService(encoder, Duration.ZERO, Clock.systemUTC()));
        assertThrows(InvalidInputException.class,
                () -> new JwtTokenService(encoder, Duration.ofMinutes(-5), Clock.systemUTC()));
    }

    @Test
    void jwtProperties_defaultsExpirationToOneHour_andHidesTheSecretInToString() {
        JwtProperties properties = new JwtProperties("super-secret-value-0123456789-abcdefghij", null);

        assertEquals(Duration.ofHours(1), properties.expiration());
        assertFalse(properties.toString().contains("super-secret-value"));
    }
}
