package com.iitsm.application.security;

import java.util.Optional;

/** Persistence contract for {@link UserCredentials}; {@code save} is an upsert keyed by user id. */
public interface CredentialsRepository {

    UserCredentials save(UserCredentials credentials);

    Optional<UserCredentials> findByUserId(String userId);
}
