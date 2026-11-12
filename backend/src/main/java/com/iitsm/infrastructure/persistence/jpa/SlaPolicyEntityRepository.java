package com.iitsm.infrastructure.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access for {@link SlaPolicyEntity}; used only by the JPA adapters. */
public interface SlaPolicyEntityRepository extends JpaRepository<SlaPolicyEntity, String> {
}
