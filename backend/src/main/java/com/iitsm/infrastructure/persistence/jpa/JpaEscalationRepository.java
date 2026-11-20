package com.iitsm.infrastructure.persistence.jpa;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.iitsm.domain.escalation.EscalationRecord;
import com.iitsm.domain.escalation.EscalationRepository;
import com.iitsm.domain.exception.InvalidInputException;

/**
 * JPA implementation of the domain {@link EscalationRepository}. {@code save} is an idempotent upsert keyed by
 * {@code (incidentId, level)}: a level already recorded for an incident is returned unchanged, so a repeated
 * escalation never creates a second row or rewrites the original timestamp. {@code @Primary} makes this the
 * production repository over the in-memory fallback declared in {@code EscalationConfig}.
 */
@Repository
@Primary
@Transactional
public class JpaEscalationRepository implements EscalationRepository {

    private final EscalationEntityRepository entities;

    public JpaEscalationRepository(EscalationEntityRepository entities) {
        this.entities = entities;
    }

    @Override
    public EscalationRecord save(EscalationRecord record) {
        if (record == null) {
            throw new InvalidInputException("record must not be null");
        }
        Optional<EscalationEntity> existing =
                entities.findByIncidentIdAndEscalationLevel(record.incidentId(), record.level());
        if (existing.isPresent()) {
            return existing.get().toDomain();
        }
        return entities.save(EscalationEntity.from(record)).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EscalationRecord> findByIncidentId(String incidentId) {
        if (isBlank(incidentId)) {
            return List.of();
        }
        return entities.findByIncidentIdOrderByEscalatedAtAscIdAsc(incidentId).stream()
                .map(EscalationEntity::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EscalationRecord> findLatestByIncidentId(String incidentId) {
        if (isBlank(incidentId)) {
            return Optional.empty();
        }
        return entities.findFirstByIncidentIdOrderByEscalatedAtDescIdDesc(incidentId).map(EscalationEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByIncidentIdAndLevel(String incidentId, int level) {
        if (isBlank(incidentId)) {
            return false;
        }
        return entities.existsByIncidentIdAndEscalationLevel(incidentId, level);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EscalationRecord> findAll() {
        return entities.findAllByOrderByIdAsc().stream().map(EscalationEntity::toDomain).toList();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
