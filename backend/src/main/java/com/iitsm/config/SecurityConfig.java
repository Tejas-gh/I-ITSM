package com.iitsm.config;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import com.iitsm.infrastructure.security.BootstrapAdminProperties;
import com.iitsm.infrastructure.security.JwtProperties;
import com.iitsm.infrastructure.security.JwtTokenService;

/**
 * Web security: stateless JWT bearer authentication.
 *
 * <p>Only the login and register endpoints (and the container's {@code /error} dispatch) are public;
 * every other request needs a valid token. Who may perform which operation stays in the application layer
 * ({@code IncidentPermissionPolicy}), the only role rule here is the staff-only SLA monitoring read endpoints. The token's
 * {@code role} claim becomes the authority {@code ROLE_<Role>}; the principal name is the user id.</p>
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({JwtProperties.class, BootstrapAdminProperties.class})
public class SecurityConfig {

    /** HS256 needs a key of at least 256 bits. */
    static final int MIN_SECRET_BYTES = 32;

    static final String AUTHORITY_PREFIX = "ROLE_";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationConverter converter) throws Exception {
        http
                // Stateless bearer-token API: no session and no cookie, so there is nothing for CSRF to protect.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/api/auth/login", "/api/auth/register", "/api/test/**", "/error").permitAll()
                        // SLA monitoring reads are for staff only; employees get 403 (POST /api/sla/monitor/run
                        // stays administrator-only in the application layer)
                        .requestMatchers(HttpMethod.GET, "/api/sla/violations", "/api/sla/at-risk")
                        .hasAnyRole("TECHNICIAN", "TEAM_LEAD", "ADMINISTRATOR", "IT_MANAGER")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)));
        return http.build();
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(JwtTokenService.ROLE_CLAIM);
        authorities.setAuthorityPrefix(AUTHORITY_PREFIX);
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    JwtEncoder jwtEncoder(JwtProperties properties) {
        return NimbusJwtEncoder.withSecretKey(secretKey(properties.secret()))
                .algorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    JwtDecoder jwtDecoder(JwtProperties properties) {
        return NimbusJwtDecoder.withSecretKey(secretKey(properties.secret()))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    /** Builds the signing key, failing fast with a clear message when the secret is missing or too short. */
    static SecretKey secretKey(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("No JWT signing secret is configured. Set the JWT_SECRET environment "
                    + "variable (property iitsm.security.jwt.secret) to a random value of at least "
                    + MIN_SECRET_BYTES + " characters.");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("The JWT signing secret is too short: it must be at least "
                    + MIN_SECRET_BYTES + " characters.");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
}
