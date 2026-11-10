package com.iitsm.domain.incident;

import com.iitsm.domain.model.IncidentStatus;

final class ResolvedState extends AbstractIncidentState {

    static final ResolvedState INSTANCE = new ResolvedState();

    private ResolvedState() {
    }

    @Override
    public IncidentStatus status() {
        return IncidentStatus.RESOLVED;
    }

    @Override
    public IncidentState verify() {
        return VerifiedState.INSTANCE;
    }

    @Override
    public IncidentState rejectResolution() {
        return InProgressState.INSTANCE;
    }

    @Override
    public boolean allowsFeedback() {
        return true;
    }
}
