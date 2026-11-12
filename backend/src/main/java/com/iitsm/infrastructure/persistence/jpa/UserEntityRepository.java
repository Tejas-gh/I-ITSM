package com.iitsm.infrastructure.persistence.jpa;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.iitsm.domain.model.Role;

/** Spring Data access for {@link UserEntity}; used only by the JPA adapters. */
public interface UserEntityRepository extends JpaRepository<UserEntity, String> {

    List<UserEntity> findByUserRoleInOrderById(Collection<Role> roles);
}
