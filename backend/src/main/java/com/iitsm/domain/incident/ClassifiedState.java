package com.iitsm.domain.incident;

import com.iitsm.domain.model.IncidentStatus;

final class ClassifiedState extends AbstractIncidentState {

    static final ClassifiedState INSTANCE = new ClassifiedState();

    private ClassifiedState() {
    }

    @Override
    public IncidentStatus status() {
        return IncidentStatus.CLASSIFIED;
    }

    @Override
    public IncidentState assign() {
        return AssignedState.INSTANCE;
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
