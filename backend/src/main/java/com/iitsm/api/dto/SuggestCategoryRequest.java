package com.iitsm.api.dto;

/** Body of {@code POST /api/incidents/suggest-category}. */
public record SuggestCategoryRequest(String title, String description) {
}
