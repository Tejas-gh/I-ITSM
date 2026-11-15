package com.iitsm.api.dto;

import java.util.Map;

import com.iitsm.application.service.TechnicianRecommendation;
import com.iitsm.domain.model.Role;

/**
 * REST response DTO for a ranked technician recommendation.
 */
public record TechnicianRecommendationResponse(
        String technicianId,
        String name,
        String email,
        Role role,
        double score,
        String reason,
        int activeWorkload,
        int maxCapacity,
        Map<String, Integer> skillLevels,
        int yearsOfExperience
) {
    public static TechnicianRecommendationResponse from(TechnicianRecommendation r) {
        return new TechnicianRecommendationResponse(
                r.technicianId(),
                r.name(),
                r.email(),
                r.role(),
                r.score(),
                r.reason(),
                r.activeWorkload(),
                r.maxCapacity(),
                r.skillLevels(),
                r.yearsOfExperience()
        );
    }
}
