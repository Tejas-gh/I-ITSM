package com.iitsm.domain.escalation;

import java.time.Instant;
import java.util.Optional;

import com.iitsm.domain.model.Role;

/**
 * Level 1 Escalation Handler in the Chain of Responsibility.
 *
 * <p>Condition: Unacknowledged P1 incident has reached or exceeded its initial
 * response deadline.</p>
 * <p>Target Role: {@link Role#TEAM_LEAD}</p>
 */
public class TeamLeadEscalationHandler extends AbstractEscalationHandler {

    @Override
    public Optional<EscalationDecision> handle(EscalationContext context) {
        if (context == null || context.incident().getResponseDeadline().isEmpty()) {
            return Optional.empty();
        }

        // If Level 1 has already been triggered, delegate to next handler in chain
        if (context.existingLevels().contains(1)) {
            return passToNext(context);
        }

        Instant deadline = context.incident().getResponseDeadline().get();
        if (!context.now().isBefore(deadline)) {
            return Optional.of(new EscalationDecision(
                    1,
                    Role.TEAM_LEAD,
                    "P1 incident unacknowledged at response deadline; escalated to Team Lead."
            ));
        }

        // Response deadline not yet reached
        return Optional.empty();
    }
}
