package com.iitsm.domain.factory;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.model.Administrator;
import com.iitsm.domain.model.Employee;
import com.iitsm.domain.model.Role;
import com.iitsm.domain.model.Technician;
import com.iitsm.domain.model.User;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserFactoryTest {

    private final UserFactory factory = new UserFactory();

    private static UserDraft draft(Role role, String department, Map<String, Integer> skills, int years, int max) {
        return new UserDraft("U1", "Sam", "sam@corp.com", role, department, skills, years, max);
    }

    @Test
    void create_employeeRole_returnsEmployeeWithDepartment() {
        User user = factory.create(draft(Role.EMPLOYEE, "Finance", null, 0, 0));
        assertEquals(Employee.class, user.getClass());
        assertEquals(Role.EMPLOYEE, user.getRole());
        assertEquals("Finance", ((Employee) user).getDepartment());
        assertEquals("U1", user.getId());
    }

    @Test
    void create_technicianAndTeamLeadRoles_returnTechnicianWithSkills() {
        Map<String, Integer> skills = Map.of("NETWORK", 3);
        User tech = factory.create(draft(Role.TECHNICIAN, null, skills, 5, 4));
        User lead = factory.create(draft(Role.TEAM_LEAD, null, skills, 8, 6));

        assertEquals(Technician.class, tech.getClass());
        assertEquals(Role.TECHNICIAN, tech.getRole());
        assertEquals(3, ((Technician) tech).skillLevelFor("NETWORK"));
        assertEquals(5, ((Technician) tech).getYearsOfExperience());
        assertEquals(4, ((Technician) tech).getMaxActiveIncidents());

        assertEquals(Technician.class, lead.getClass());
        assertEquals(Role.TEAM_LEAD, lead.getRole());
        assertEquals(6, ((Technician) lead).getMaxActiveIncidents());
    }

    @Test
    void create_administratorAndItManagerRoles_returnAdministrator() {
        User admin = factory.create(draft(Role.ADMINISTRATOR, null, null, 0, 0));
        User manager = factory.create(draft(Role.IT_MANAGER, null, null, 0, 0));

        assertEquals(Administrator.class, admin.getClass());
        assertEquals(Role.ADMINISTRATOR, admin.getRole());
        assertEquals(Administrator.class, manager.getClass());
        assertEquals(Role.IT_MANAGER, manager.getRole());
    }

    @Test
    void create_employeeWithoutDepartment_throwsInvalidInput() {
        assertThrows(InvalidInputException.class, () -> factory.create(draft(Role.EMPLOYEE, null, null, 0, 0)));
        assertThrows(InvalidInputException.class, () -> factory.create(draft(Role.EMPLOYEE, "  ", null, 0, 0)));
    }

    @Test
    void create_technicianWithoutSkills_throwsInvalidInput() {
        assertThrows(InvalidInputException.class, () -> factory.create(draft(Role.TECHNICIAN, null, null, 1, 3)));
        assertThrows(InvalidInputException.class, () -> factory.create(draft(Role.TEAM_LEAD, null, null, 1, 3)));
    }

    @Test
    void create_technicianWithInvalidLimits_throwsInvalidInput() {
        Map<String, Integer> skills = Map.of();
        assertThrows(InvalidInputException.class, () -> factory.create(draft(Role.TECHNICIAN, null, skills, -1, 3)));
        assertThrows(InvalidInputException.class, () -> factory.create(draft(Role.TECHNICIAN, null, skills, 1, 0)));
    }

    @Test
    void create_nullDraft_throwsInvalidInput() {
        assertThrows(InvalidInputException.class, () -> factory.create(null));
    }

    @Test
    void userDraft_nullRequiredFields_throwInvalidInput() {
        assertThrows(InvalidInputException.class,
                () -> new UserDraft(null, "Sam", "sam@corp.com", Role.EMPLOYEE, "HR", null, 0, 0));
        assertThrows(InvalidInputException.class,
                () -> new UserDraft("U1", null, "sam@corp.com", Role.EMPLOYEE, "HR", null, 0, 0));
        assertThrows(InvalidInputException.class,
                () -> new UserDraft("U1", "Sam", null, Role.EMPLOYEE, "HR", null, 0, 0));
        assertThrows(InvalidInputException.class,
                () -> new UserDraft("U1", "Sam", "sam@corp.com", null, "HR", null, 0, 0));
    }
}
