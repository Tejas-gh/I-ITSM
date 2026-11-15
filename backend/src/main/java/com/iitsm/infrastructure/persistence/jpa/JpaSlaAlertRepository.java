package com.iitsm.infrastructure.persistence.jpa;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.sla.SlaAlert;
import com.iitsm.domain.sla.SlaAlertKind;
import com.iitsm.domain.sla.SlaAlertRepository;
import com.iitsm.domain.sla.SlaType;

/**
 * JPA implementation of the domain {@link SlaAlertRepository}. {@code save} is an upsert keyed by
 * {@code (incidentId, slaType, kind)}, matching the in-memory implementation. {@code @Primary} makes this the
 * production repository over the in-memory fallback declared in {@code SlaMonitorConfig}.
 */
@Repository
@Primary
@Transactional
public class JpaSlaAlertRepository implements SlaAlertRepository {

    private final SlaAlertEntityRepository entities;

    public JpaSlaAlertRepository(SlaAlertEntityRepository entities) {
        this.entities = entities;
    }

    @Override
    public void save(SlaAlert alert) {
        if (alert == null) {
            throw new InvalidInputException("alert must not be null");
        }
        Optional<SlaAlertEntity> existing =
                entities.findByIncidentIdAndSlaTypeAndKind(alert.incidentId(), alert.slaType(), alert.kind());
        if (existing.isPresent()) {
            existing.get().copyMutableFrom(alert);
            entities.save(existing.get());
        } else {
            entities.save(SlaAlertEntity.from(alert));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean exists(String incidentId, SlaType slaType, SlaAlertKind kind) {
        if (isBlank(incidentId) || slaType == null || kind == null) {
            return false;
        }
        return entities.existsByIncidentIdAndSlaTypeAndKind(incidentId, slaType, kind);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SlaAlert> findByKey(String incidentId, SlaType slaType, SlaAlertKind kind) {
        if (isBlank(incidentId) || slaType == null || kind == null) {
            return Optional.empty();
        }
        return entities.findByIncidentIdAndSlaTypeAndKind(incidentId, slaType, kind).map(SlaAlertEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SlaAlert> findById(String id) {
        if (isBlank(id)) {
            return Optional.empty();
        }
        return entities.findByAlertId(id).map(SlaAlertEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SlaAlert> findAll() {
        return entities.findAllByOrderBySeqAsc().stream().map(SlaAlertEntity::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SlaAlert> findByKind(SlaAlertKind kind) {
        if (kind == null) {
            throw new InvalidInputException("kind must not be null");
        }
        return entities.findByKindOrderBySeqAsc(kind).stream().map(SlaAlertEntity::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SlaAlert> findByIncidentId(String incidentId) {
        if (isBlank(incidentId)) {
            throw new InvalidInputException("incidentId must not be blank");
        }
        return entities.findByIncidentIdOrderBySeqAsc(incidentId).stream().map(SlaAlertEntity::toDomain).toList();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
