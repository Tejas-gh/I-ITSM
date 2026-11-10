package com.iitsm.infrastructure.persistence.memory;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.model.Technician;
import com.iitsm.domain.model.User;
import com.iitsm.domain.repository.UserRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Thread-safe in-memory implementation of {@link UserRepository} backed by a {@link LinkedHashMap}.
 */
public final class InMemoryUserRepository implements UserRepository {

    private final Map<String, User> storage = new LinkedHashMap<>();

    @Override
    public synchronized User save(User user) {
        if (user == null) {
            throw new InvalidInputException("user must not be null");
        }
        storage.put(user.getId(), user);
        return user;
    }

    @Override
    public synchronized Optional<User> findById(String id) {
        if (id == null) {
            throw new InvalidInputException("id must not be null");
        }
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public synchronized List<User> findAll() {
        return Collections.unmodifiableList(new ArrayList<>(storage.values()));
    }

    @Override
    public synchronized List<Technician> findAllTechnicians() {
        List<Technician> technicians = new ArrayList<>();
        for (User user : storage.values()) {
            if (user instanceof Technician tech) {
                technicians.add(tech);
            }
        }
        return Collections.unmodifiableList(technicians);
    }
}

