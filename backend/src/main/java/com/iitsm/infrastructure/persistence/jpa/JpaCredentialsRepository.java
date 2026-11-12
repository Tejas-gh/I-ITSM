package com.iitsm.infrastructure.persistence.jpa;

import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.iitsm.application.security.CredentialsRepository;
import com.iitsm.application.security.UserCredentials;
import com.iitsm.domain.exception.InvalidInputException;

/** JPA implementation of {@link CredentialsRepository} (single {@code user_credentials} table). */
@Repository
@Transactional
public class JpaCredentialsRepository implements CredentialsRepository {

    private final CredentialsEntityRepository entities;

    public JpaCredentialsRepository(CredentialsEntityRepository entities) {
        this.entities = entities;
    }

    @Override
    public UserCredentials save(UserCredentials credentials) {
        if (credentials == null) {
            throw new InvalidInputException("credentials must not be null");
        }
        entities.save(CredentialsEntity.from(credentials));
        return credentials;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserCredentials> findByUserId(String userId) {
        if (userId == null) {
            throw new InvalidInputException("userId must not be null");
        }
        return entities.findById(userId).map(CredentialsEntity::toDomain);
    }
}
