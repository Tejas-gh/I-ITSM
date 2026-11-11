package com.iitsm.application.report;

/**
 * Real-time active incident allocation, capacity limits, and utilization percentage for a technician.
 */
public record TechnicianWorkloadEntry(
        String technicianId,
        String technicianName,
        int activeIncidents,
        int maxActiveIncidents,
        double capacityUtilization
) {
}
