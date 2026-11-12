package com.iitsm.infrastructure.persistence.jpa;

import com.iitsm.application.security.UserCredentials;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** JPA structure for the {@code user_credentials} table (BCrypt hash only). */
@Entity
@Table(name = "user_credentials")
public class CredentialsEntity {

    @Id
    @Column(name = "user_id")
    String userId;

    @Column(name = "password_hash")
    String passwordHash;

    protected CredentialsEntity() {
    }

    static CredentialsEntity from(UserCredentials credentials) {
        CredentialsEntity e = new CredentialsEntity();
        e.userId = credentials.userId();
        e.passwordHash = credentials.passwordHash();
        return e;
    }

    UserCredentials toDomain() {
        return new UserCredentials(userId, passwordHash);
    }
}
