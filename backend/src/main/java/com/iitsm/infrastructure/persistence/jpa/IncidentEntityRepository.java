package com.iitsm.infrastructure.persistence.jpa;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.iitsm.domain.model.IncidentStatus;

/** Spring Data access for {@link IncidentEntity}; used only by the JPA adapters. */
public interface IncidentEntityRepository extends JpaRepository<IncidentEntity, String> {

    List<IncidentEntity> findAllByOrderByCreatedAtAscIdAsc();

    List<IncidentEntity> findByReporterIdOrderByCreatedAtAscIdAsc(String reporterId);

    List<IncidentEntity> findByAssignedTechnicianIdOrderByCreatedAtAscIdAsc(String technicianId);

    long countByAssignedTechnicianIdAndStatusIn(String technicianId, Collection<IncidentStatus> statuses);
}
