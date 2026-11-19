package com.iitsm.api.dto;

/**
 * Request payload for checking potential duplicate open incidents.
 */
public record CheckDuplicateRequest(
        String title,
        String description,
        String serviceId,
        String categoryId
) {
}
