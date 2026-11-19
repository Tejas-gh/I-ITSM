package com.iitsm.domain.escalation;

import java.time.Instant;
import java.util.Optional;

import com.iitsm.domain.model.Role;

/**
 * Level 3 Escalation Handler in the Chain of Responsibility.
 *
 * <p>Condition: Unacknowledged P1 incident has exceeded response deadline by +2 response target durations.</p>
 * <p>Target Role: {@link Role#IT_MANAGER}</p>
 */
public class ItManagerEscalationHandler extends AbstractEscalationHandler {

    @Override
    public Optional<EscalationDecision> handle(EscalationContext context) {
        if (context == null || context.incident().getResponseDeadline().isEmpty()) {
            return Optional.empty();
        }

        // If Level 3 has already been triggered, delegate to next handler in chain (tail)
        if (context.existingLevels().contains(3)) {
            return passToNext(context);
        }

        Instant threshold = context.incident().getResponseDeadline().get().plus(context.responseTargetDuration().multipliedBy(2));
        if (!context.now().isBefore(threshold)) {
            return Optional.of(new EscalationDecision(
                    3,
                    Role.IT_MANAGER,
                    "P1 incident unacknowledged after +2 response targets; escalated to IT Manager."
            ));
        }

        return Optional.empty();
    }
}
