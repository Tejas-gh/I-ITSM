package com.iitsm.application.command;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.model.CategorySource;
import com.iitsm.domain.model.Impact;
import com.iitsm.domain.model.Urgency;

public record CreateIncidentCommand(
        String actorId,
        String title,
        String description,
        String serviceId,
        String categoryId,
        String suggestedCategoryId,
        CategorySource categorySource,
        Impact impact,
        Urgency urgency,
        int affectedUsers
) {

    public CreateIncidentCommand {
        if (actorId == null || actorId.isBlank()) {
            throw new InvalidInputException("actorId must not be blank");
        }
        if (serviceId == null || serviceId.isBlank()) {
            throw new InvalidInputException("serviceId must not be blank");
        }
        if (categoryId == null || categoryId.isBlank()) {
            throw new InvalidInputException("categoryId must not be blank");
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
    }
}
