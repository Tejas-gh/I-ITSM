package com.iitsm.domain.incident;

import com.iitsm.domain.model.IncidentStatus;

final class InProgressState extends AbstractIncidentState {

    static final InProgressState INSTANCE = new InProgressState();

    private InProgressState() {
    }

    @Override
    public IncidentStatus status() {
        return IncidentStatus.IN_PROGRESS;
    }

    @Override
    public IncidentState requestInfo() {
        return WaitingForUserState.INSTANCE;
    }

    @Override
    public IncidentState resolve() {
        return ResolvedState.INSTANCE;
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
