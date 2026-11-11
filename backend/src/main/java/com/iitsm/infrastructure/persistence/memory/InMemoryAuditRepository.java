package com.iitsm.infrastructure.persistence.memory;

import com.iitsm.domain.audit.AuditEntry;
import com.iitsm.domain.audit.AuditRepository;
import com.iitsm.domain.exception.InvalidInputException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe in-memory implementation of {@link AuditRepository}.
 */
public final class InMemoryAuditRepository implements AuditRepository {

    private final List<AuditEntry> entries = new CopyOnWriteArrayList<>();

    @Override
    public void save(AuditEntry entry) {
        if (entry == null) {
            throw new InvalidInputException("entry must not be null");
        }
        entries.add(entry);
    }

    @Override
    public List<AuditEntry> findByIncidentId(String incidentId) {
        if (incidentId == null || incidentId.isBlank()) {
            throw new InvalidInputException("incidentId must not be null or blank");
        }
        List<AuditEntry> result = new ArrayList<>();
        for (AuditEntry entry : entries) {
            if (incidentId.equals(entry.incidentId())) {
                result.add(entry);
            }
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public List<AuditEntry> findAll() {
        return Collections.unmodifiableList(new ArrayList<>(entries));
    }
}
