package com.iitsm.domain.factory;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.incident.IncidentDraft;
import com.iitsm.domain.priority.PriorityInput;
import com.iitsm.domain.priority.PriorityResult;
import com.iitsm.domain.priority.PriorityStrategy;

import java.time.Instant;

/**
 * Assembles a new {@link Incident} from a validated draft: generates an id, computes priority, and constructs the aggregate.
 * Does NOT apply SLA deadlines — that is the service's responsibility (Q-N2).
 */
public final class IncidentFactory {

    private final IncidentIdGenerator ids;
    private final PriorityStrategy priorityStrategy;

    public IncidentFactory(IncidentIdGenerator ids, PriorityStrategy priorityStrategy) {
        if (ids == null) {
            throw new InvalidInputException("ids must not be null");
        }
        if (priorityStrategy == null) {
            throw new InvalidInputException("priorityStrategy must not be null");
        }
        this.ids = ids;
        this.priorityStrategy = priorityStrategy;
    }

    /**
     * Creates a new Incident with status NEW and no SLA deadlines.
     *
     * @param draft     validated incident inputs
     * @param createdAt the creation instant supplied by the caller (no Instant.now() here)
     * @return the newly constructed Incident
     */
    public Incident create(IncidentDraft draft, Instant createdAt) {
        if (draft == null) {
            throw new InvalidInputException("draft must not be null");
        }
        if (createdAt == null) {
            throw new InvalidInputException("createdAt must not be null");
        }
        String id = ids.nextIncidentId();
        PriorityResult result = priorityStrategy.calculate(
                new PriorityInput(
                        draft.impact(),
                        draft.urgency(),
                        draft.service().getBusinessCriticality(),
                        draft.affectedUsers()
                )
        );
        return new Incident(id, draft, result, createdAt);
    }
}

