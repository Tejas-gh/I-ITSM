package com.iitsm.api.dto;

/** Body of {@code PUT /api/admin/technicians/{id}/availability}. */
public record AvailabilityRequest(Boolean available) {
}
