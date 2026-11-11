package com.iitsm.application.report;

/**
 * Summary overview metrics for incidents across the organization.
 */
public record IncidentOverview(
        long totalIncidents,
        long openIncidents,
        long criticalIncidents,
        long resolvedIncidents,
        long breachedIncidents
) {
}
