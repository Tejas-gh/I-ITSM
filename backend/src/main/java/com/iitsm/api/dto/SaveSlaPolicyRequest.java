package com.iitsm.api.dto;

import com.iitsm.domain.model.PriorityLevel;

import java.time.Duration;

/**
 * Request payload for configuring default or override SLA policies.
 */
public record SaveSlaPolicyRequest(
        String serviceId,
        PriorityLevel priority,
        Duration responseTarget,
        Duration resolutionTarget
) {
}
