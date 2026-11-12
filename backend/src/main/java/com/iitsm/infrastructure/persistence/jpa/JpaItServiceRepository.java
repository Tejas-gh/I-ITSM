package com.iitsm.infrastructure.persistence.jpa;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.model.ItService;
import com.iitsm.domain.repository.ItServiceRepository;

/** JPA implementation of the domain {@link ItServiceRepository}. */
@Repository
@Transactional
public class JpaItServiceRepository implements ItServiceRepository {

    private final ItServiceEntityRepository entities;

    public JpaItServiceRepository(ItServiceEntityRepository entities) {
        this.entities = entities;
    }

    @Override
    public ItService save(ItService service) {
        if (service == null) {
            throw new InvalidInputException("service must not be null");
        }
        entities.save(ItServiceEntity.from(service));
        return service;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ItService> findById(String id) {
        if (id == null) {
            throw new InvalidInputException("id must not be null");
        }
        return entities.findById(id).map(ItServiceEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ItService> findAll() {
        return entities.findAll(Sort.by("id")).stream().map(ItServiceEntity::toDomain).toList();
    }
}
