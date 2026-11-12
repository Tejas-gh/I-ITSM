package com.iitsm.infrastructure.persistence.jpa;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.model.Role;
import com.iitsm.domain.model.Technician;
import com.iitsm.domain.model.User;
import com.iitsm.domain.repository.UserRepository;

/** JPA implementation of the domain {@link UserRepository} (single {@code users} table + skills). */
@Repository
@Transactional
public class JpaUserRepository implements UserRepository {

    private final UserEntityRepository entities;

    public JpaUserRepository(UserEntityRepository entities) {
        this.entities = entities;
    }

    @Override
    public User save(User user) {
        if (user == null) {
            throw new InvalidInputException("user must not be null");
        }
        entities.save(UserEntity.from(user));
        return user;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findById(String id) {
        if (id == null) {
            throw new InvalidInputException("id must not be null");
        }
        return entities.findById(id).map(UserEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> findAll() {
        return entities.findAll(Sort.by("id")).stream().map(UserEntity::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Technician> findAllTechnicians() {
        return entities.findByUserRoleInOrderById(List.of(Role.TECHNICIAN, Role.TEAM_LEAD)).stream()
                .map(e -> (Technician) e.toDomain())
                .toList();
    }
}
