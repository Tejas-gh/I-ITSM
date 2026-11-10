package com.iitsm.domain.incident;

import com.iitsm.domain.model.IncidentStatus;

final class AssignedState extends AbstractIncidentState {

    static final AssignedState INSTANCE = new AssignedState();

    private AssignedState() {
    }

    @Override
    public IncidentStatus status() {
        return IncidentStatus.ASSIGNED;
    }

    @Override
    public IncidentState acknowledge() {
        return AcknowledgedState.INSTANCE;
    }

    @Override
    public IncidentState unassign() {
        return ClassifiedState.INSTANCE;
    }

    @Override
    public boolean allowsAdditionalInfo() {
        return true;
    }

    @Override
    public boolean allowsPriorityChange() {
        return true;
    }
}
