package com.iitsm.api.dto;

import java.util.Map;

import com.iitsm.domain.model.Role;
import com.iitsm.domain.model.Technician;

/** A technician as returned by the admin API ({@code assignable} = active and available). */
public record TechnicianResponse(String id, String name, String email, Role role, boolean active, boolean available,
                                 boolean assignable, Map<String, Integer> skillLevels, int yearsOfExperience,
                                 int maxActiveIncidents) {

    public static TechnicianResponse from(Technician technician) {
        return new TechnicianResponse(technician.getId(), technician.getName(), technician.getEmail(),
                technician.getRole(), technician.isActive(), technician.isAvailable(), technician.isAssignable(),
                technician.getSkillLevels(), technician.getYearsOfExperience(), technician.getMaxActiveIncidents());
    }
}
