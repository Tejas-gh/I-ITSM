package com.iitsm.api.dto;

import com.iitsm.application.report.TechnicianWorkloadEntry;

/**
 * Technician workload and capacity metrics.
 */
public record TechnicianWorkloadResponse(
        String technicianId,
        String technicianName,
        int activeIncidents,
        int maxActiveIncidents,
        double capacityUtilization
) {
    public static TechnicianWorkloadResponse from(TechnicianWorkloadEntry e) {
        return new TechnicianWorkloadResponse(
                e.technicianId(),
                e.technicianName(),
                e.activeIncidents(),
                e.maxActiveIncidents(),
                e.capacityUtilization()
        );
    }
}
