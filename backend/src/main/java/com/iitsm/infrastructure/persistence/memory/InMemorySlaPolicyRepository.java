package com.iitsm.infrastructure.persistence.memory;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.model.PriorityLevel;
import com.iitsm.domain.model.SlaPolicy;
import com.iitsm.domain.repository.SlaPolicyRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Thread-safe in-memory implementation of {@link SlaPolicyRepository} backed by a {@link LinkedHashMap}.
 * Overrides are keyed by "serviceId|priority", and default policies are keyed by "*|priority".
 */
public final class InMemorySlaPolicyRepository implements SlaPolicyRepository {

    private final Map<String, SlaPolicy> storage = new LinkedHashMap<>();

    private static String key(String serviceId, PriorityLevel priority) {
        String svc = (serviceId == null) ? "*" : serviceId;
        return svc + "|" + priority.name();
    }

    @Override
    public synchronized SlaPolicy save(SlaPolicy policy) {
        if (policy == null) {
            throw new InvalidInputException("policy must not be null");
        }
        storage.put(key(policy.serviceId(), policy.priority()), policy);
        return policy;
    }

    @Override
    public synchronized Optional<SlaPolicy> findOverride(String serviceId, PriorityLevel priority) {
        if (serviceId == null) {
            throw new InvalidInputException("serviceId must not be null");
        }
        if (priority == null) {
            throw new InvalidInputException("priority must not be null");
        }
        return Optional.ofNullable(storage.get(key(serviceId, priority)));
    }

    @Override
    public synchronized Optional<SlaPolicy> findDefault(PriorityLevel priority) {
        if (priority == null) {
            throw new InvalidInputException("priority must not be null");
        }
        return Optional.ofNullable(storage.get(key(null, priority)));
    }

    @Override
    public synchronized List<SlaPolicy> findAll() {
        return Collections.unmodifiableList(new ArrayList<>(storage.values()));
    }
}

