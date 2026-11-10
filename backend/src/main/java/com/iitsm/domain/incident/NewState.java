package com.iitsm.domain.incident;

import com.iitsm.domain.model.IncidentStatus;

final class NewState extends AbstractIncidentState {

    static final NewState INSTANCE = new NewState();

    private NewState() {
    }

    @Override
    public IncidentStatus status() {
        return IncidentStatus.NEW;
    }

    @Override
    public IncidentState classify() {
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
