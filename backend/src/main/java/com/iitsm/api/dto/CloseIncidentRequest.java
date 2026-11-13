package com.iitsm.api.dto;

/** Body of {@code POST /api/incidents/{id}/close}; the closure note is optional. */
public record CloseIncidentRequest(String closureNote) {
}
