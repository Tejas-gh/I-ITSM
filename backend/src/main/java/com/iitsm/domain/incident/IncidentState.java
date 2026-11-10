package com.iitsm.domain.incident;

import com.iitsm.domain.model.IncidentStatus;

/**
 * State contract for the Incident lifecycle.
 * Transition methods are pure: they return the next state or throw
 * {@link com.iitsm.domain.exception.InvalidStateTransitionException}.
 */
public interface IncidentState {

    IncidentStatus status();

    IncidentState classify();

    IncidentState assign();

    IncidentState unassign();

    IncidentState acknowledge();

    IncidentState startWork();

    IncidentState requestInfo();

    IncidentState receiveInfo();

    IncidentState resolve();

    IncidentState rejectResolution();

    IncidentState verify();

    IncidentState close();

    boolean allowsInvestigationNotes();

    boolean allowsAdditionalInfo();

    boolean allowsFeedback();

    boolean allowsPriorityChange();
}
