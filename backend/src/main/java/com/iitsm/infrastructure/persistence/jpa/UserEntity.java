package com.iitsm.infrastructure.persistence.jpa;

import java.util.HashMap;
import java.util.Map;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.model.Administrator;
import com.iitsm.domain.model.Employee;
import com.iitsm.domain.model.Role;
import com.iitsm.domain.model.Technician;
import com.iitsm.domain.model.User;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;

/**
 * JPA structure for the single {@code users} table plus {@code technician_skills}.
 * The concrete domain type is chosen from {@code userRole} when mapping back.
 */
@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    String id;
    String name;
    String email;
    @Enumerated(EnumType.STRING)
    Role userRole;
    boolean active;
    String department;
    Integer yearsOfExperience;
    Integer maxActiveIncidents;
    Boolean available;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "technician_skills", joinColumns = @JoinColumn(name = "technician_id"))
    @MapKeyColumn(name = "category_id")
    @Column(name = "skill_level")
    Map<String, Integer> skills = new HashMap<>();

    protected UserEntity() {
    }

    static UserEntity from(User user) {
        UserEntity e = new UserEntity();
        e.id = user.getId();
        e.name = user.getName();
        e.email = user.getEmail();
        e.userRole = user.getRole();
        e.active = user.isActive();
        if (user instanceof Employee employee) {
            e.department = employee.getDepartment();
        } else if (user instanceof Technician technician) {
            e.yearsOfExperience = technician.getYearsOfExperience();
            e.maxActiveIncidents = technician.getMaxActiveIncidents();
            e.available = technician.isAvailable();
            e.skills = new HashMap<>(technician.getSkillLevels());
        } else if (!(user instanceof Administrator)) {
            throw new InvalidInputException("Unsupported user type: " + user.getClass().getName());
        }
        return e;
    }

    User toDomain() {
        User user = switch (userRole) {
            case EMPLOYEE -> new Employee(id, name, email, department);
            case TECHNICIAN, TEAM_LEAD -> {
                Technician technician = new Technician(id, name, email, userRole, new HashMap<>(skills),
                        yearsOfExperience, maxActiveIncidents);
                if (!Boolean.TRUE.equals(available)) {
                    technician.markUnavailable();
                }
                yield technician;
            }
            case ADMINISTRATOR, IT_MANAGER -> new Administrator(id, name, email, userRole);
        };
        if (!active) {
            user.deactivate();
        }
        return user;
    }
}
