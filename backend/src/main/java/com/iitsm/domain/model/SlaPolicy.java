package com.iitsm.domain.model;

import com.iitsm.domain.exception.InvalidInputException;

import java.time.Duration;

public record SlaPolicy(String serviceId, PriorityLevel priority,
                        Duration responseTarget, Duration resolutionTarget) {

    public SlaPolicy {
        if (priority == null) {
            throw new InvalidInputException("priority must not be null");
        }
        if (responseTarget == null || responseTarget.isZero() || responseTarget.isNegative()) {
            throw new InvalidInputException("responseTarget must be positive");
        }
        if (resolutionTarget == null || resolutionTarget.isZero() || resolutionTarget.isNegative()) {
            throw new InvalidInputException("resolutionTarget must be positive");
        }
        if (responseTarget.compareTo(resolutionTarget) > 0) {
            throw new InvalidInputException("responseTarget must not exceed resolutionTarget");
        }
    }

    public boolean isDefault() {
        return serviceId == null;
    }
}
