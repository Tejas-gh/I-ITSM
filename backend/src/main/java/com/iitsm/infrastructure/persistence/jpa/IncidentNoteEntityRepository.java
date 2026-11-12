package com.iitsm.infrastructure.persistence.jpa;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access for {@link IncidentNoteEntity}; used only by the JPA adapters. */
public interface IncidentNoteEntityRepository extends JpaRepository<IncidentNoteEntity, Long> {

    List<IncidentNoteEntity> findByIncidentIdOrderByIdAsc(String incidentId);

    List<IncidentNoteEntity> findByIncidentIdInOrderByIdAsc(Collection<String> incidentIds);

    long countByIncidentId(String incidentId);

    void deleteByIncidentId(String incidentId);
}
