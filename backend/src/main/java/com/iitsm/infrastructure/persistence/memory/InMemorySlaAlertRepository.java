package com.iitsm.infrastructure.persistence.memory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.sla.SlaAlert;
import com.iitsm.domain.sla.SlaAlertKind;
import com.iitsm.domain.sla.SlaAlertRepository;
import com.iitsm.domain.sla.SlaType;

/**
 * Thread-safe in-memory implementation of {@link SlaAlertRepository}.
 *
 * <p>Uses a synchronized {@link LinkedHashMap} keyed by the alert's natural idempotency key
 * ({@code incidentId:slaType:kind}) for O(1) existence checks and insertion-ordered retrieval.</p>
 */
public final class InMemorySlaAlertRepository implements SlaAlertRepository {

    private final Map<String, SlaAlert> store = new LinkedHashMap<>();

    @Override
    public synchronized void save(SlaAlert alert) {
        if (alert == null) {
            throw new InvalidInputException("alert must not be null");
        }
        store.put(alert.alertKey(), alert);
    }

    @Override
    public synchronized boolean exists(String incidentId, SlaType slaType, SlaAlertKind kind) {
        if (incidentId == null || incidentId.isBlank() || slaType == null || kind == null) {
            return false;
        }
        return store.containsKey(SlaAlert.key(incidentId, slaType, kind));
    }

    @Override
    public synchronized Optional<SlaAlert> findByKey(String incidentId, SlaType slaType, SlaAlertKind kind) {
        if (incidentId == null || incidentId.isBlank() || slaType == null || kind == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(store.get(SlaAlert.key(incidentId, slaType, kind)));
    }

    @Override
    public synchronized Optional<SlaAlert> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        // Direct key lookup first
        SlaAlert alert = store.get(id);
        if (alert != null) {
            return Optional.of(alert);
        }
        // Fallback scan by alert id if custom id was provided
        for (SlaAlert a : store.values()) {
            if (id.equals(a.id())) {
                return Optional.of(a);
            }
        }
        return Optional.empty();
    }

    @Override
    public synchronized List<SlaAlert> findAll() {
        return Collections.unmodifiableList(new ArrayList<>(store.values()));
    }

    @Override
    public synchronized List<SlaAlert> findByKind(SlaAlertKind kind) {
        if (kind == null) {
            throw new InvalidInputException("kind must not be null");
        }
        List<SlaAlert> result = new ArrayList<>();
        for (SlaAlert alert : store.values()) {
            if (alert.kind() == kind) {
                result.add(alert);
            }
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public synchronized List<SlaAlert> findByIncidentId(String incidentId) {
        if (incidentId == null || incidentId.isBlank()) {
            throw new InvalidInputException("incidentId must not be blank");
        }
        List<SlaAlert> result = new ArrayList<>();
        for (SlaAlert alert : store.values()) {
            if (incidentId.equals(alert.incidentId())) {
                result.add(alert);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Clears all in-memory alerts (test support helper).
     */
    public synchronized void clear() {
        store.clear();
    }
}
