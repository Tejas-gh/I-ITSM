package com.iitsm.infrastructure.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access for {@link ItServiceEntity}; used only by the JPA adapters. */
public interface ItServiceEntityRepository extends JpaRepository<ItServiceEntity, String> {
}
