package com.iitsm.api.dto;

import com.iitsm.application.report.ServiceIncidentCount;

/**
 * Incident distribution count per IT service.
 */
public record ServiceIncidentCountResponse(
        String serviceId,
        String serviceName,
        long incidentCount
) {
    public static ServiceIncidentCountResponse from(ServiceIncidentCount c) {
        return new ServiceIncidentCountResponse(
                c.serviceId(),
                c.serviceName(),
                c.incidentCount()
        );
    }
}
