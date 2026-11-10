package com.iitsm.domain.incident;

import com.iitsm.domain.model.IncidentStatus;

final class VerifiedState extends AbstractIncidentState {

    static final VerifiedState INSTANCE = new VerifiedState();

    private VerifiedState() {
    }

    @Override
    public IncidentStatus status() {
        return IncidentStatus.VERIFIED;
    }

    @Override
    public IncidentState close() {
        return ClosedState.INSTANCE;
    }

    @Override
    public boolean allowsFeedback() {
        return true;
    }
}
