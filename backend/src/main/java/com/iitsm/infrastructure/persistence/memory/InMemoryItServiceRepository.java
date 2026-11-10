package com.iitsm.infrastructure.persistence.memory;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.model.ItService;
import com.iitsm.domain.repository.ItServiceRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Thread-safe in-memory implementation of {@link ItServiceRepository} backed by a {@link LinkedHashMap}.
 */
public final class InMemoryItServiceRepository implements ItServiceRepository {

    private final Map<String, ItService> storage = new LinkedHashMap<>();

    @Override
    public synchronized ItService save(ItService service) {
        if (service == null) {
            throw new InvalidInputException("service must not be null");
        }
        storage.put(service.getId(), service);
        return service;
    }

    @Override
    public synchronized Optional<ItService> findById(String id) {
        if (id == null) {
            throw new InvalidInputException("id must not be null");
        }
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public synchronized List<ItService> findAll() {
        return Collections.unmodifiableList(new ArrayList<>(storage.values()));
    }
}

