package com.iitsm.api.dto;

/** Body of {@code PUT /api/admin/assignment-strategy}: the name of the strategy to activate, e.g. "load-based". */
public record StrategySelectionRequest(String strategy) {
}
