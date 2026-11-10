package com.iitsm.domain.exception;

import com.iitsm.domain.model.IncidentStatus;

public class InvalidStateTransitionException extends ItsmException {
    private final IncidentStatus currentStatus;
    private final String action;

    public InvalidStateTransitionException(IncidentStatus currentStatus, String action) {
        super("INVALID_STATE_TRANSITION",
                "Cannot " + action + " an incident that is " + currentStatus);
        this.currentStatus = currentStatus;
        this.action = action;
    }

    public IncidentStatus getCurrentStatus() {
        return currentStatus;
    }

    public String getAction() {
        return action;
    }
}
