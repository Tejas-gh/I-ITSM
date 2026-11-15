package com.iitsm.application.service;

/**
 * Result of evaluating SLA for a single incident during a monitor tick.
 */
public record IncidentSlaResult(
        int warningsRaised,
        int violationsRaised
) {
    public static final IncidentSlaResult EMPTY = new IncidentSlaResult(0, 0);
}
