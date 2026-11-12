package com.iitsm.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.authentication.AbstractAuthenticationToken;

import com.iitsm.domain.model.Role;

class SecurityConfigTest {

    private final JwtAuthenticationConverter converter = new SecurityConfig().jwtAuthenticationConverter();

    private static Jwt tokenWith(String subject, String role) {
        Instant now = Instant.parse("2026-11-16T09:00:00Z");
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600));
        if (role != null) {
            builder.claim("role", role);
        }
        return builder.build();
    }

    /**
     * Only the role authorities: Spring Security 7.1 also adds its own FACTOR_BEARER authority to every
     * bearer-token authentication, which is not part of our role mapping.
     */
    private static List<String> roleAuthorities(AbstractAuthenticationToken authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(name -> name.startsWith(SecurityConfig.AUTHORITY_PREFIX))
                .toList();
    }

    @Test
    void converter_everyDomainRole_becomesExactlyOneRolePrefixedAuthority() {
        for (Role role : Role.values()) {
            AbstractAuthenticationToken authentication = converter.convert(tokenWith("U1", role.name()));

            assertEquals(List.of("ROLE_" + role.name()), roleAuthorities(authentication));
        }
    }

    @Test
    void converter_principalName_isTheUserId() {
        AbstractAuthenticationToken authentication = converter.convert(tokenWith("E100", "EMPLOYEE"));

        assertEquals("E100", authentication.getName());
    }

    @Test
    void converter_tokenWithoutRoleClaim_hasNoRoleAuthority() {
        AbstractAuthenticationToken authentication = converter.convert(tokenWith("E100", null));

        assertTrue(roleAuthorities(authentication).isEmpty());
    }

    @Test
    void secretKey_validSecret_buildsAnHmacSha256Key() {
        assertEquals("HmacSHA256", SecurityConfig.secretKey("0123456789abcdef0123456789abcdef").getAlgorithm());
    }

    @Test
    void secretKey_missingOrBlankSecret_failsFastMentioningJwtSecret() {
        IllegalStateException missing = assertThrows(IllegalStateException.class, () -> SecurityConfig.secretKey(null));
        IllegalStateException blank = assertThrows(IllegalStateException.class, () -> SecurityConfig.secretKey("  "));

        assertTrue(missing.getMessage().contains("JWT_SECRET"));
        assertTrue(blank.getMessage().contains("JWT_SECRET"));
    }

    @Test
    void secretKey_tooShortSecret_isRejected() {
        assertThrows(IllegalStateException.class, () -> SecurityConfig.secretKey("0123456789abcdef0123456789abcde"));
    }
}
