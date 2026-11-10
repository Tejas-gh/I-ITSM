package com.iitsm.domain.priority;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.model.PriorityLevel;

public record PriorityResult(int score, PriorityLevel level) {

    public PriorityResult {
        if (score < 1) {
            throw new InvalidInputException("score must be >= 1");
        }
        if (level == null) {
            throw new InvalidInputException("level must not be null");
        }
    }
}
