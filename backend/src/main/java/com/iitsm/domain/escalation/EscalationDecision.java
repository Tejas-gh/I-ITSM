package com.iitsm.domain.escalation;

import java.util.Objects;

import com.iitsm.domain.model.Role;

/**
 * Result produced by an escalation handler in the Chain of Responsibility when an
 * escalation threshold is reached.
 */
public record EscalationDecision(
        int level,
        Role targetRole,
        String reason
) {
    public EscalationDecision {
        Objects.requireNonNull(targetRole, "targetRole must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
    }
}
