package com.iitsm.domain.incident;

import com.iitsm.domain.model.IncidentStatus;

final class ClosedState extends AbstractIncidentState {

    static final ClosedState INSTANCE = new ClosedState();

    private ClosedState() {
    }

    @Override
    public IncidentStatus status() {
        return IncidentStatus.CLOSED;
    }

    @Override
    public boolean allowsFeedback() {
        return true;
    }
}
