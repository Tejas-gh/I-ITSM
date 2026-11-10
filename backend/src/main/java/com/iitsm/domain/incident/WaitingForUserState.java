package com.iitsm.domain.incident;

import com.iitsm.domain.model.IncidentStatus;

final class WaitingForUserState extends AbstractIncidentState {

    static final WaitingForUserState INSTANCE = new WaitingForUserState();

    private WaitingForUserState() {
    }

    @Override
    public IncidentStatus status() {
        return IncidentStatus.WAITING_FOR_USER;
    }

    @Override
    public IncidentState receiveInfo() {
        return InProgressState.INSTANCE;
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
