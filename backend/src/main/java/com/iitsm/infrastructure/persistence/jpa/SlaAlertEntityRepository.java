package com.iitsm.infrastructure.persistence.jpa;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.iitsm.domain.sla.SlaAlertKind;
import com.iitsm.domain.sla.SlaType;

/** Spring Data access for {@link SlaAlertEntity}; used only by the JPA adapter. */
public interface SlaAlertEntityRepository extends JpaRepository<SlaAlertEntity, Long> {

    Optional<SlaAlertEntity> findByIncidentIdAndSlaTypeAndKind(String incidentId, SlaType slaType, SlaAlertKind kind);

    boolean existsByIncidentIdAndSlaTypeAndKind(String incidentId, SlaType slaType, SlaAlertKind kind);

    Optional<SlaAlertEntity> findByAlertId(String alertId);

    List<SlaAlertEntity> findAllByOrderBySeqAsc();

    List<SlaAlertEntity> findByKindOrderBySeqAsc(SlaAlertKind kind);

    List<SlaAlertEntity> findByIncidentIdOrderBySeqAsc(String incidentId);
}
