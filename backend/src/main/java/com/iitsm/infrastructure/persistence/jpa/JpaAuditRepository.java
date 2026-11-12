package com.iitsm.infrastructure.persistence.jpa;

import java.util.List;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.iitsm.domain.audit.AuditEntry;
import com.iitsm.domain.audit.AuditRepository;
import com.iitsm.domain.exception.InvalidInputException;

/** JPA implementation of the domain {@link AuditRepository}; append-only, ordered by insertion. */
@Repository
@Transactional
public class JpaAuditRepository implements AuditRepository {

    private final AuditEntryEntityRepository entities;

    public JpaAuditRepository(AuditEntryEntityRepository entities) {
        this.entities = entities;
    }

    @Override
    public void save(AuditEntry entry) {
        if (entry == null) {
            throw new InvalidInputException("entry must not be null");
        }
        entities.save(AuditEntryEntity.from(entry));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditEntry> findByIncidentId(String incidentId) {
        if (incidentId == null || incidentId.isBlank()) {
            throw new InvalidInputException("incidentId must not be null or blank");
        }
        return entities.findByIncidentIdOrderByIdAsc(incidentId).stream().map(AuditEntryEntity::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditEntry> findAll() {
        return entities.findAllByOrderByIdAsc().stream().map(AuditEntryEntity::toDomain).toList();
    }
}
