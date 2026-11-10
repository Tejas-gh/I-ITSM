package com.iitsm.domain.incident;

import com.iitsm.domain.model.IncidentStatus;

final class AcknowledgedState extends AbstractIncidentState {

    static final AcknowledgedState INSTANCE = new AcknowledgedState();

    private AcknowledgedState() {
    }

    @Override
    public IncidentStatus status() {
        return IncidentStatus.ACKNOWLEDGED;
    }

    @Override
    public IncidentState startWork() {
        return InProgressState.INSTANCE;
    }

    @Override
    public IncidentState unassign() {
        return ClassifiedState.INSTANCE;
    }

    @Override
    public boolean allowsInvestigationNotes() {
        return true;
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
