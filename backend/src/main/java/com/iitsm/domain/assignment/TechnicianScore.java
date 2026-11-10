package com.iitsm.domain.assignment;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.model.Technician;

public record TechnicianScore(Technician technician, double score, String reason) {

    public TechnicianScore {
        if (technician == null) {
            throw new InvalidInputException("technician must not be null");
        }
        if (Double.isNaN(score)) {
            throw new InvalidInputException("score must not be NaN");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new InvalidInputException("reason must not be blank");
        }
    }
}
