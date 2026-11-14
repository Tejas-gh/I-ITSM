package com.iitsm.api.dto;

/**
 * Request payload for creating or updating an incident category.
 */
public record SaveCategoryRequest(
        String id,
        String name,
        Boolean active
) {
}
