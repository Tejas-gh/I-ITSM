package com.iitsm.infrastructure.persistence.jpa;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access for {@link CategoryEntity}; used only by the JPA adapters. */
public interface CategoryEntityRepository extends JpaRepository<CategoryEntity, String> {

    List<CategoryEntity> findByActiveTrueOrderById();
}
