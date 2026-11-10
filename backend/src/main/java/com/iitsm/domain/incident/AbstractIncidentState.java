package com.iitsm.domain.incident;

import com.iitsm.domain.exception.InvalidStateTransitionException;

/**
 * Base incident state providing default "throw on transition" and "false for guards" behavior.
 */
abstract class AbstractIncidentState implements IncidentState {

    @Override
    public IncidentState classify() {
        throw new InvalidStateTransitionException(status(), "classify");
    }

    @Override
    public IncidentState assign() {
        throw new InvalidStateTransitionException(status(), "assign");
    }

    @Override
    public IncidentState unassign() {
        throw new InvalidStateTransitionException(status(), "unassign");
    }

    @Override
    public IncidentState acknowledge() {
        throw new InvalidStateTransitionException(status(), "acknowledge");
    }

    @Override
    public IncidentState startWork() {
        throw new InvalidStateTransitionException(status(), "startWork");
    }

    @Override
    public IncidentState requestInfo() {
        throw new InvalidStateTransitionException(status(), "requestInfo");
    }

    @Override
    public IncidentState receiveInfo() {
        throw new InvalidStateTransitionException(status(), "receiveInfo");
    }

    @Override
    public IncidentState resolve() {
        throw new InvalidStateTransitionException(status(), "resolve");
    }

    @Override
    public IncidentState rejectResolution() {
        throw new InvalidStateTransitionException(status(), "rejectResolution");
    }

    @Override
    public IncidentState verify() {
        throw new InvalidStateTransitionException(status(), "verify");
    }

    @Override
    public IncidentState close() {
        throw new InvalidStateTransitionException(status(), "close");
    }

    @Override
    public boolean allowsInvestigationNotes() {
        return false;
    }

    @Override
    public boolean allowsAdditionalInfo() {
        return false;
    }

    @Override
    public boolean allowsFeedback() {
        return false;
    }

    @Override
    public boolean allowsPriorityChange() {
        return false;
    }
}
