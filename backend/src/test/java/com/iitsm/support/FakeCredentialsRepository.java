package com.iitsm.support;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.iitsm.application.security.CredentialsRepository;
import com.iitsm.application.security.UserCredentials;

/** In-memory {@link CredentialsRepository} for plain unit tests. */
public final class FakeCredentialsRepository implements CredentialsRepository {

    private final Map<String, UserCredentials> byUserId = new HashMap<>();

    @Override
    public UserCredentials save(UserCredentials credentials) {
        byUserId.put(credentials.userId(), credentials);
        return credentials;
    }

    @Override
    public Optional<UserCredentials> findByUserId(String userId) {
        return Optional.ofNullable(byUserId.get(userId));
    }

    public int size() {
        return byUserId.size();
    }
}
