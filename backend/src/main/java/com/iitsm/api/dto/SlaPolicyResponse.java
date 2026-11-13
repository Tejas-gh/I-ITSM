package com.iitsm.api.dto;

import com.iitsm.domain.model.PriorityLevel;
import com.iitsm.domain.model.SlaPolicy;

import java.time.Duration;

/**
 * Representation of an SLA policy returned over HTTP.
 */
public record SlaPolicyResponse(
        String serviceId,
        PriorityLevel priority,
        Duration responseTarget,
        Duration resolutionTarget
) {
    public static SlaPolicyResponse from(SlaPolicy policy) {
        return new SlaPolicyResponse(
                policy.serviceId(),
                policy.priority(),
                policy.responseTarget(),
                policy.resolutionTarget()
        );
    }
}
