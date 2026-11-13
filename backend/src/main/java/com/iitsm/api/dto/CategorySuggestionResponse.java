package com.iitsm.api.dto;

import com.iitsm.domain.model.CategorySource;

/** A category suggestion returned by {@code POST /api/incidents/suggest-category}. */
public record CategorySuggestionResponse(String categoryId, double confidence, String reason, CategorySource source) {
}
