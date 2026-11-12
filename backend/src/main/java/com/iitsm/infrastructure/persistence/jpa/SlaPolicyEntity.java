package com.iitsm.infrastructure.persistence.jpa;

import java.time.Duration;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.model.PriorityLevel;
import com.iitsm.domain.model.SlaPolicy;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** JPA structure for {@code sla_policies}; primary key is the §M key ({@code serviceId|P1} or {@code *|P1}). */
@Entity
@Table(name = "sla_policies")
public class SlaPolicyEntity {

    @Id
    String policyKey;
    String serviceId;
    @Enumerated(EnumType.STRING)
    PriorityLevel priority;
    long responseTargetSeconds;
    long resolutionTargetSeconds;

    protected SlaPolicyEntity() {
    }

    static String key(String serviceId, PriorityLevel priority) {
        return (serviceId == null ? "*" : serviceId) + "|" + priority.name();
    }

    static SlaPolicyEntity from(SlaPolicy policy) {
        if (policy.responseTarget().getNano() != 0 || policy.resolutionTarget().getNano() != 0) {
            throw new InvalidInputException("SLA targets must be whole seconds to be persisted");
        }
        SlaPolicyEntity e = new SlaPolicyEntity();
        e.policyKey = key(policy.serviceId(), policy.priority());
        e.serviceId = policy.serviceId();
        e.priority = policy.priority();
        e.responseTargetSeconds = policy.responseTarget().getSeconds();
        e.resolutionTargetSeconds = policy.resolutionTarget().getSeconds();
        return e;
    }

    SlaPolicy toDomain() {
        return new SlaPolicy(serviceId, priority, Duration.ofSeconds(responseTargetSeconds),
                Duration.ofSeconds(resolutionTargetSeconds));
    }
}
