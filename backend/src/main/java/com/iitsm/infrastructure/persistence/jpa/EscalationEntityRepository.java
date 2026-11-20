package com.iitsm.infrastructure.persistence.jpa;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access for {@link EscalationEntity}; used only by the JPA adapter. */
public interface EscalationEntityRepository extends JpaRepository<EscalationEntity, Long> {

    Optional<EscalationEntity> findByIncidentIdAndEscalationLevel(String incidentId, int escalationLevel);

    boolean existsByIncidentIdAndEscalationLevel(String incidentId, int escalationLevel);

    List<EscalationEntity> findByIncidentIdOrderByEscalatedAtAscIdAsc(String incidentId);

    Optional<EscalationEntity> findFirstByIncidentIdOrderByEscalatedAtDescIdDesc(String incidentId);

    List<EscalationEntity> findAllByOrderByIdAsc();
}
