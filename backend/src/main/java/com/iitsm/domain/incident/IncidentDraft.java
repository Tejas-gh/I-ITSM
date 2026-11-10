package com.iitsm.domain.incident;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.model.Category;
import com.iitsm.domain.model.CategorySource;
import com.iitsm.domain.model.Impact;
import com.iitsm.domain.model.ItService;
import com.iitsm.domain.model.Urgency;
import com.iitsm.domain.model.User;

/**
 * Validated draft carrying all reporter-supplied inputs needed to create an incident.
 * Moved to the incident package (R2) to avoid a package cycle with the factory.
 */
public record IncidentDraft(
        String title,
        String description,
        User reporter,
        ItService service,
        Category category,
        Category suggestedCategory,     // nullable
        CategorySource categorySource,
        Impact impact,
        Urgency urgency,
        int affectedUsers
) {

    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_DESCRIPTION_LENGTH = 4000;

    public IncidentDraft {
        if (title == null || title.trim().isEmpty()) {
            throw new InvalidInputException("title must not be blank");
        }
        title = title.trim();
        if (title.length() > MAX_TITLE_LENGTH) {
            throw new InvalidInputException("title must not exceed " + MAX_TITLE_LENGTH + " characters");
        }
        if (description == null || description.trim().isEmpty()) {
            throw new InvalidInputException("description must not be blank");
        }
        description = description.trim();
        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new InvalidInputException("description must not exceed " + MAX_DESCRIPTION_LENGTH + " characters");
        }
        if (reporter == null) {
            throw new InvalidInputException("reporter must not be null");
        }
        if (service == null) {
            throw new InvalidInputException("service must not be null");
        }
        if (category == null) {
            throw new InvalidInputException("category must not be null");
        }
        if (!category.isActive()) {
            throw new InvalidInputException("category must be active");
        }
        if (categorySource == null) {
            throw new InvalidInputException("categorySource must not be null");
        }
        if (impact == null) {
            throw new InvalidInputException("impact must not be null");
        }
        if (urgency == null) {
            throw new InvalidInputException("urgency must not be null");
        }
        if (affectedUsers < 1) {
            throw new InvalidInputException("affectedUsers must be >= 1");
        }
        // If categorySource != MANUAL then suggestedCategory must be non-null and its id must equal category's id
        if (categorySource != CategorySource.MANUAL) {
            if (suggestedCategory == null) {
                throw new InvalidInputException(
                        "suggestedCategory must not be null when categorySource is " + categorySource);
            }
            if (!suggestedCategory.getId().equals(category.getId())) {
                throw new InvalidInputException(
                        "suggestedCategory id must equal category id when categorySource is " + categorySource);
            }
        }
    }
}

