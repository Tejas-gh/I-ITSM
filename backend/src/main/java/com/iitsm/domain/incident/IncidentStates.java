package com.iitsm.domain.incident;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.model.IncidentStatus;

/**
 * Factory and lookup for singleton {@link IncidentState} implementations.
 */
public final class IncidentStates {

    private IncidentStates() {
    }

    public static IncidentState initial() {
        return of(IncidentStatus.NEW);
    }

    public static IncidentState of(IncidentStatus status) {
        if (status == null) {
            throw new InvalidInputException("status must not be null");
        }
        return switch (status) {
            case NEW -> NewState.INSTANCE;
            case CLASSIFIED -> ClassifiedState.INSTANCE;
            case ASSIGNED -> AssignedState.INSTANCE;
            case ACKNOWLEDGED -> AcknowledgedState.INSTANCE;
            case IN_PROGRESS -> InProgressState.INSTANCE;
            case WAITING_FOR_USER -> WaitingForUserState.INSTANCE;
            case RESOLVED -> ResolvedState.INSTANCE;
            case VERIFIED -> VerifiedState.INSTANCE;
            case CLOSED -> ClosedState.INSTANCE;
        };
    }
}
