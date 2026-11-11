package com.iitsm.domain.sla;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.model.PriorityLevel;
import com.iitsm.domain.model.SlaPolicy;

import java.time.Instant;

/**
 * Calculates response and resolution deadlines from the resolved SLA policy and creation timestamp.
 */
public final class SlaCalculator {

    private final SlaPolicyResolver resolver;

    public SlaCalculator(SlaPolicyResolver resolver) {
        if (resolver == null) {
            throw new InvalidInputException("resolver must not be null");
        }
        this.resolver = resolver;
    }

    public SlaDeadlines calculate(String serviceId, PriorityLevel priority, Instant createdAt) {
        if (priority == null) {
            throw new InvalidInputException("priority must not be null");
        }
        if (createdAt == null) {
            throw new InvalidInputException("createdAt must not be null");
        }

        SlaPolicy policy = resolver.resolve(serviceId, priority);
        Instant responseDeadline = createdAt.plus(policy.responseTarget());
        Instant resolutionDeadline = createdAt.plus(policy.resolutionTarget());

        return new SlaDeadlines(responseDeadline, resolutionDeadline);
    }
}
