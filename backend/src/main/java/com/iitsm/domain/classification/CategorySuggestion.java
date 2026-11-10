package com.iitsm.domain.classification;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.model.CategorySource;

public record CategorySuggestion(String categoryId, double confidence, String reason, CategorySource source) {

    public CategorySuggestion {
        if (categoryId == null || categoryId.trim().isEmpty()) {
            throw new InvalidInputException("categoryId must not be blank");
        }
        if (!(confidence >= 0.0 && confidence <= 1.0)) {
            throw new InvalidInputException("confidence must be between 0.0 and 1.0");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new InvalidInputException("reason must not be blank");
        }
        if (source == null || source == CategorySource.MANUAL) {
            throw new InvalidInputException("source must be non-null and not MANUAL");
        }
    }
}
