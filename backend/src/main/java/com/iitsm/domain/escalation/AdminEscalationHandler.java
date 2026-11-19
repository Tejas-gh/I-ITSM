package com.iitsm.domain.escalation;

import java.time.Instant;
import java.util.Optional;

import com.iitsm.domain.model.Role;

/**
 * Level 2 Escalation Handler in the Chain of Responsibility.
 *
 * <p>Condition: Unacknowledged P1 incident has exceeded response deadline by +1 response target duration.</p>
 * <p>Target Role: {@link Role#ADMINISTRATOR}</p>
 */
public class AdminEscalationHandler extends AbstractEscalationHandler {

    @Override
    public Optional<EscalationDecision> handle(EscalationContext context) {
        if (context == null || context.incident().getResponseDeadline().isEmpty()) {
            return Optional.empty();
        }

        // If Level 2 has already been triggered, delegate to next handler in chain
        if (context.existingLevels().contains(2)) {
            return passToNext(context);
        }

        Instant threshold = context.incident().getResponseDeadline().get().plus(context.responseTargetDuration());
        if (!context.now().isBefore(threshold)) {
            return Optional.of(new EscalationDecision(
                    2,
                    Role.ADMINISTRATOR,
                    "P1 incident unacknowledged after +1 response target; escalated to Administrator."
            ));
        }

        return Optional.empty();
    }
}
