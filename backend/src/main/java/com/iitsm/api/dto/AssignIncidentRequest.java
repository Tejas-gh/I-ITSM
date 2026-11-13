package com.iitsm.api.dto;

/** Body of {@code POST /api/incidents/{id}/assign} and {@code /reassign}. */
public record AssignIncidentRequest(String technicianId) {
}
