package com.iitsm.application.command;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.model.PriorityLevel;

import java.time.Duration;

/**
 * Command for creating or updating an SLA policy (default or per-service override).
 */
public record SaveSlaPolicyCommand(
        String actorId,
        String serviceId,
        PriorityLevel priority,
        Duration responseTarget,
        Duration resolutionTarget
) {
    public SaveSlaPolicyCommand {
        if (actorId == null || actorId.isBlank()) {
            throw new InvalidInputException("actorId must not be blank");
        }
        if (priority == null) {
            throw new InvalidInputException("priority must not be null");
        }
        if (responseTarget == null || responseTarget.isNegative() || responseTarget.isZero()) {
            throw new InvalidInputException("responseTarget must be positive");
        }
        if (resolutionTarget == null || resolutionTarget.isNegative() || resolutionTarget.isZero()) {
            throw new InvalidInputException("resolutionTarget must be positive");
        }
        if (responseTarget.compareTo(resolutionTarget) > 0) {
            throw new InvalidInputException("responseTarget must not exceed resolutionTarget");
        }
    }
}
