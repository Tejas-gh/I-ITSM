package com.iitsm.domain.sla;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.exception.MissingSlaException;
import com.iitsm.domain.model.PriorityLevel;
import com.iitsm.domain.model.SlaPolicy;
import com.iitsm.domain.repository.SlaPolicyRepository;

import java.util.Optional;

/**
 * Resolves SLA policies by checking for a per-service override first,
 * falling back to the default priority policy, or throwing {@link MissingSlaException}.
 */
public final class SlaPolicyResolver {

    private final SlaPolicyRepository policies;

    public SlaPolicyResolver(SlaPolicyRepository policies) {
        if (policies == null) {
            throw new InvalidInputException("policies repository must not be null");
        }
        this.policies = policies;
    }

    public SlaPolicy resolve(String serviceId, PriorityLevel priority) {
        if (priority == null) {
            throw new InvalidInputException("priority must not be null");
        }

        if (serviceId != null && !serviceId.isBlank()) {
            Optional<SlaPolicy> override = policies.findOverride(serviceId, priority);
            if (override.isPresent()) {
                return override.get();
            }
        }

        Optional<SlaPolicy> defaultPolicy = policies.findDefault(priority);
        if (defaultPolicy.isPresent()) {
            return defaultPolicy.get();
        }

        throw new MissingSlaException("No SLA policy for service " + serviceId + " and priority " + priority);
    }
}
