package com.iitsm.application.report;

/**
 * Incident distribution count associated with a specific IT service.
 */
public record ServiceIncidentCount(
        String serviceId,
        String serviceName,
        long incidentCount
) {
}
