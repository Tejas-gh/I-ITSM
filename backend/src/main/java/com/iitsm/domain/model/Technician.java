package com.iitsm.domain.model;

import com.iitsm.domain.exception.InvalidInputException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Technician extends User {

    private static final int MIN_SKILL_LEVEL = 1;
    private static final int MAX_SKILL_LEVEL = 5;

    private final Map<String, Integer> skillLevels;
    private final int yearsOfExperience;
    private final int maxActiveIncidents;
    private boolean available = true;

    public Technician(String id, String name, String email, Role role,
                      Map<String, Integer> skillLevels, int yearsOfExperience, int maxActiveIncidents) {
        super(id, name, email, role);
        if (role != Role.TECHNICIAN && role != Role.TEAM_LEAD) {
            throw new InvalidInputException("role must be TECHNICIAN or TEAM_LEAD");
        }
        if (skillLevels == null) {
            throw new InvalidInputException("skillLevels must not be null");
        }
        Map<String, Integer> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : skillLevels.entrySet()) {
            String key = entry.getKey();
            Integer value = entry.getValue();
            if (key == null || key.trim().isEmpty()) {
                throw new InvalidInputException("skill category id must not be blank");
            }
            if (value == null || value < MIN_SKILL_LEVEL || value > MAX_SKILL_LEVEL) {
                throw new InvalidInputException("skill level must be between 1 and 5");
            }
            copy.put(key, value);
        }
        if (yearsOfExperience < 0) {
            throw new InvalidInputException("yearsOfExperience must be >= 0");
        }
        if (maxActiveIncidents < 1) {
            throw new InvalidInputException("maxActiveIncidents must be >= 1");
        }
        this.skillLevels = Collections.unmodifiableMap(copy);
        this.yearsOfExperience = yearsOfExperience;
        this.maxActiveIncidents = maxActiveIncidents;
    }

    public Map<String, Integer> getSkillLevels() {
        return skillLevels;
    }

    public int skillLevelFor(String categoryId) {
        return skillLevels.getOrDefault(categoryId, 0);
    }

    public boolean hasSkill(String categoryId) {
        return skillLevelFor(categoryId) > 0;
    }

    public int getYearsOfExperience() {
        return yearsOfExperience;
    }

    public int getMaxActiveIncidents() {
        return maxActiveIncidents;
    }

    public boolean isAvailable() {
        return available;
    }

    public void markAvailable() {
        this.available = true;
    }

    public void markUnavailable() {
        this.available = false;
    }

    public boolean isAssignable() {
        return isActive() && isAvailable();
    }

    public boolean isOverloaded(int activeIncidents) {
        return activeIncidents >= maxActiveIncidents;
    }
}
