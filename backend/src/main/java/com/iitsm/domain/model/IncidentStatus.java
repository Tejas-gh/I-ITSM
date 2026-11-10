package com.iitsm.domain.model;

/** Lifecycle status of an incident. */
public enum IncidentStatus {
    NEW,
    CLASSIFIED,
    ASSIGNED,
    ACKNOWLEDGED,
    IN_PROGRESS,
    WAITING_FOR_USER,
    RESOLVED,
    VERIFIED,
    CLOSED;

    /** True for statuses in which the incident counts toward its technician's active workload. */
    public boolean countsTowardWorkload() {
        return switch (this) {
            case ASSIGNED, ACKNOWLEDGED, IN_PROGRESS, WAITING_FOR_USER -> true;
            default -> false;
        };
    }
}
