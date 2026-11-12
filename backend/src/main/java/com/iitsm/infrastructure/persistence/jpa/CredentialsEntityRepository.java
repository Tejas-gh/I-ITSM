package com.iitsm.infrastructure.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access for {@link CredentialsEntity}; used only by {@link JpaCredentialsRepository}. */
public interface CredentialsEntityRepository extends JpaRepository<CredentialsEntity, String> {
}
