package com.iitsm.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.iitsm.application.security.CredentialsRepository;
import com.iitsm.application.security.PasswordHasher;
import com.iitsm.application.security.TokenIssuer;
import com.iitsm.application.service.AuthService;
import com.iitsm.domain.factory.UserFactory;
import com.iitsm.domain.repository.UserRepository;
import com.iitsm.infrastructure.security.BCryptPasswordHasher;
import com.iitsm.infrastructure.security.BootstrapAdminInitializer;
import com.iitsm.infrastructure.security.BootstrapAdminProperties;
import com.iitsm.infrastructure.security.JwtProperties;
import com.iitsm.infrastructure.security.JwtTokenService;

/** Wires the plain authentication classes (A5) as beans. */
@Configuration
public class AuthConfig {

    @Bean
    UserFactory userFactory() {
        return new UserFactory();
    }

    @Bean
    PasswordHasher passwordHasher() {
        return new BCryptPasswordHasher();
    }

    @Bean
    TokenIssuer tokenIssuer(JwtEncoder jwtEncoder, JwtProperties properties) {
        return new JwtTokenService(jwtEncoder, properties.expiration(), Clock.systemUTC());
    }

    @Bean
    AuthService authService(UserRepository users, CredentialsRepository credentials, PasswordHasher passwordHasher,
                            TokenIssuer tokenIssuer, UserFactory userFactory) {
        return new AuthService(users, credentials, passwordHasher, tokenIssuer, userFactory);
    }

    /** Runs at startup in its own transaction, so the admin and its credentials are created together or not at all. */
    @Bean
    BootstrapAdminInitializer bootstrapAdminInitializer(AuthService authService, BootstrapAdminProperties properties,
                                                        PlatformTransactionManager transactionManager) {
        return new BootstrapAdminInitializer(authService, properties, new TransactionTemplate(transactionManager));
    }
}
