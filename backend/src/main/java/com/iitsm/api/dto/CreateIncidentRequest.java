package com.iitsm.api.dto;

import com.iitsm.domain.model.CategorySource;
import com.iitsm.domain.model.Impact;
import com.iitsm.domain.model.Urgency;

/** Body of {@code POST /api/incidents}; the reporter is always the authenticated user. */
public record CreateIncidentRequest(
        String title,
        String description,
        String serviceId,
        String categoryId,
        String suggestedCategoryId,
        CategorySource categorySource,
        Impact impact,
        Urgency urgency,
        int affectedUsers) {
}
