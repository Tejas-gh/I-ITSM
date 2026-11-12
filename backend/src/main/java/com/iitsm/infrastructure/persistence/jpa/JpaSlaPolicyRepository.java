package com.iitsm.infrastructure.persistence.jpa;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.model.PriorityLevel;
import com.iitsm.domain.model.SlaPolicy;
import com.iitsm.domain.repository.SlaPolicyRepository;

/** JPA implementation of the domain {@link SlaPolicyRepository}; save replaces the same (serviceId, priority). */
@Repository
@Transactional
public class JpaSlaPolicyRepository implements SlaPolicyRepository {

    private final SlaPolicyEntityRepository entities;

    public JpaSlaPolicyRepository(SlaPolicyEntityRepository entities) {
        this.entities = entities;
    }

    @Override
    public SlaPolicy save(SlaPolicy policy) {
        if (policy == null) {
            throw new InvalidInputException("policy must not be null");
        }
        entities.save(SlaPolicyEntity.from(policy));
        return policy;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SlaPolicy> findOverride(String serviceId, PriorityLevel priority) {
        if (serviceId == null) {
            throw new InvalidInputException("serviceId must not be null");
        }
        if (priority == null) {
            throw new InvalidInputException("priority must not be null");
        }
        return entities.findById(SlaPolicyEntity.key(serviceId, priority)).map(SlaPolicyEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SlaPolicy> findDefault(PriorityLevel priority) {
        if (priority == null) {
            throw new InvalidInputException("priority must not be null");
        }
        return entities.findById(SlaPolicyEntity.key(null, priority)).map(SlaPolicyEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SlaPolicy> findAll() {
        return entities.findAll(Sort.by("policyKey")).stream().map(SlaPolicyEntity::toDomain).toList();
    }
}
