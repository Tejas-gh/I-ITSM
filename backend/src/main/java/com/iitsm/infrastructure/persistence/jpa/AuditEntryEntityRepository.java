package com.iitsm.infrastructure.persistence.jpa;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access for {@link AuditEntryEntity}; used only by the JPA adapters. */
public interface AuditEntryEntityRepository extends JpaRepository<AuditEntryEntity, Long> {

    List<AuditEntryEntity> findByIncidentIdOrderByIdAsc(String incidentId);

    List<AuditEntryEntity> findAllByOrderByIdAsc();
}
