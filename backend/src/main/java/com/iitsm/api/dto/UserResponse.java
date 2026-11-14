package com.iitsm.api.dto;

import java.util.Map;

import com.iitsm.domain.model.Employee;
import com.iitsm.domain.model.Role;
import com.iitsm.domain.model.Technician;
import com.iitsm.domain.model.User;

/**
 * A user as returned by the admin API. Never contains a password or password hash. Role-specific fields are null
 * when they do not apply: {@code department} for employees; skills, experience, workload limit and availability for
 * technicians.
 */
public record UserResponse(String id, String name, String email, Role role, boolean active, String department,
                           Map<String, Integer> skillLevels, Integer yearsOfExperience, Integer maxActiveIncidents,
                           Boolean available) {

    public static UserResponse from(User user) {
        String department = user instanceof Employee employee ? employee.getDepartment() : null;
        if (user instanceof Technician technician) {
            return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole(), user.isActive(),
                    null, technician.getSkillLevels(), technician.getYearsOfExperience(),
                    technician.getMaxActiveIncidents(), technician.isAvailable());
        }
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole(), user.isActive(),
                department, null, null, null, null);
    }
}
