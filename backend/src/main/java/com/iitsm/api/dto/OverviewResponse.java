package com.iitsm.api.dto;

import com.iitsm.application.report.IncidentOverview;

/**
 * Summary metrics of incidents across the organization.
 */
public record OverviewResponse(
        long totalIncidents,
        long openIncidents,
        long criticalIncidents,
        long resolvedIncidents,
        long breachedIncidents
) {
    public static OverviewResponse from(IncidentOverview o) {
        return new OverviewResponse(
                o.totalIncidents(),
                o.openIncidents(),
                o.criticalIncidents(),
                o.resolvedIncidents(),
                o.breachedIncidents()
        );
    }
}
