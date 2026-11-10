package com.iitsm.infrastructure.persistence.memory;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.repository.IncidentRepository;
import com.iitsm.domain.repository.IncidentSearchCriteria;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Thread-safe in-memory implementation of {@link IncidentRepository} backed by a {@link LinkedHashMap}.
 * Retains insertion order and uses same-reference storage.
 */
public final class InMemoryIncidentRepository implements IncidentRepository {

    private final Map<String, Incident> storage = new LinkedHashMap<>();

    @Override
    public synchronized Incident save(Incident incident) {
        if (incident == null) {
            throw new InvalidInputException("incident must not be null");
        }
        storage.put(incident.getId(), incident);
        return incident;
    }

    @Override
    public synchronized Optional<Incident> findById(String id) {
        if (id == null) {
            throw new InvalidInputException("id must not be null");
        }
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public synchronized List<Incident> findAll() {
        return Collections.unmodifiableList(new ArrayList<>(storage.values()));
    }

    @Override
    public synchronized List<Incident> findByReporterId(String reporterId) {
        if (reporterId == null) {
            throw new InvalidInputException("reporterId must not be null");
        }
        List<Incident> result = new ArrayList<>();
        for (Incident inc : storage.values()) {
            if (inc.getReporter().getId().equals(reporterId)) {
                result.add(inc);
            }
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public synchronized List<Incident> findByAssignedTechnicianId(String technicianId) {
        if (technicianId == null) {
            throw new InvalidInputException("technicianId must not be null");
        }
        List<Incident> result = new ArrayList<>();
        for (Incident inc : storage.values()) {
            if (inc.getAssignedTechnician().isPresent()
                    && inc.getAssignedTechnician().get().getId().equals(technicianId)) {
                result.add(inc);
            }
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public synchronized int countActiveByTechnicianId(String technicianId) {
        if (technicianId == null) {
            throw new InvalidInputException("technicianId must not be null");
        }
        int count = 0;
        for (Incident inc : storage.values()) {
            if (inc.getAssignedTechnician().isPresent()
                    && inc.getAssignedTechnician().get().getId().equals(technicianId)
                    && inc.getStatus().countsTowardWorkload()) {
                count++;
            }
        }
        return count;
    }

    @Override
    public synchronized List<Incident> search(IncidentSearchCriteria criteria) {
        if (criteria == null) {
            throw new InvalidInputException("criteria must not be null");
        }
        List<Incident> result = new ArrayList<>();
        for (Incident inc : storage.values()) {
            if (matches(inc, criteria)) {
                result.add(inc);
            }
        }
        return Collections.unmodifiableList(result);
    }

    private boolean matches(Incident inc, IncidentSearchCriteria criteria) {
        if (criteria.incidentId() != null && !inc.getId().equals(criteria.incidentId())) {
            return false;
        }
        if (criteria.createdFrom() != null && inc.getCreatedAt().isBefore(criteria.createdFrom())) {
            return false;
        }
        if (criteria.createdTo() != null && inc.getCreatedAt().isAfter(criteria.createdTo())) {
            return false;
        }
        if (criteria.status() != null && inc.getStatus() != criteria.status()) {
            return false;
        }
        if (criteria.priority() != null && inc.getPriority() != criteria.priority()) {
            return false;
        }
        if (criteria.categoryId() != null && !inc.getCategory().getId().equals(criteria.categoryId())) {
            return false;
        }
        if (criteria.serviceId() != null && !inc.getService().getId().equals(criteria.serviceId())) {
            return false;
        }
        if (criteria.technicianId() != null) {
            if (inc.getAssignedTechnician().isEmpty()
                    || !inc.getAssignedTechnician().get().getId().equals(criteria.technicianId())) {
                return false;
            }
        }
        return true;
    }
}

