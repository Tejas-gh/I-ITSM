package com.iitsm.api.dto;

import com.iitsm.domain.model.Category;

/**
 * Representation of an incident category returned over HTTP.
 */
public record CategoryResponse(
        String id,
        String name,
        boolean active
) {
    public static CategoryResponse from(Category c) {
        return new CategoryResponse(c.getId(), c.getName(), c.isActive());
    }
}
