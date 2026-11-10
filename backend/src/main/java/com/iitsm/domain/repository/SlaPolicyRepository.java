package com.iitsm.domain.repository;

import com.iitsm.domain.model.PriorityLevel;
import com.iitsm.domain.model.SlaPolicy;

import java.util.List;
import java.util.Optional;

public interface SlaPolicyRepository {

    SlaPolicy save(SlaPolicy p);                       // replaces same (serviceId, priority)

    Optional<SlaPolicy> findOverride(String serviceId, PriorityLevel priority);

    Optional<SlaPolicy> findDefault(PriorityLevel priority);

    List<SlaPolicy> findAll();
}
