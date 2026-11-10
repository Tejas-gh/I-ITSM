package com.iitsm.domain.model;

import com.iitsm.domain.exception.InvalidInputException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserHierarchyTest {

    private static Employee employee(String id) {
        return new Employee(id, "Alice", "alice@corp.com", "Finance");
    }

    private static Technician technician(Role role, Map<String, Integer> skills, int years, int max) {
        return new Technician("T1", "Tom", "tom@corp.com", role, skills, years, max);
    }

    @Test
    void constructor_blankIdOrName_throwsInvalidInput() {
        assertThrows(InvalidInputException.class, () -> new Employee(null, "Alice", "a@b.com", "Finance"));
        assertThrows(InvalidInputException.class, () -> new Employee("  ", "Alice", "a@b.com", "Finance"));
        assertThrows(InvalidInputException.class, () -> new Employee("E1", "", "a@b.com", "Finance"));
        assertThrows(InvalidInputException.class, () -> new Employee("E1", "   ", "a@b.com", "Finance"));
        assertThrows(InvalidInputException.class, () -> new Employee("E1", "Alice", "a@b.com", " "));
    }

    @Test
    void constructor_invalidEmail_throwsInvalidInput() {
        for (String bad : new String[]{null, "", "no-at-sign", "@corp.com", "alice@", "a@@b.com", "a@b@c.com", " @corp.com"}) {
            assertThrows(InvalidInputException.class,
                    () -> new Employee("E1", "Alice", bad, "Finance"), "email: " + bad);
        }
    }

    @Test
    void constructor_validInput_storesTrimmedValuesAndFixedRole() {
        Employee e = new Employee(" E1 ", " Alice ", " alice@corp.com ", " Finance ");
        assertEquals("E1", e.getId());
        assertEquals("Alice", e.getName());
        assertEquals("alice@corp.com", e.getEmail());
        assertEquals("Finance", e.getDepartment());
        assertEquals(Role.EMPLOYEE, e.getRole());
        assertTrue(e.isActive());
        assertEquals("EMPLOYEE E1", e.toString());
    }

    @Test
    void constructor_roleMismatch_throwsInvalidInput() {
        Map<String, Integer> skills = Map.of("NETWORK", 3);
        assertThrows(InvalidInputException.class, () -> technician(Role.EMPLOYEE, skills, 1, 3));
        assertThrows(InvalidInputException.class, () -> technician(Role.ADMINISTRATOR, skills, 1, 3));
        assertThrows(InvalidInputException.class, () -> technician(Role.IT_MANAGER, skills, 1, 3));
        assertThrows(InvalidInputException.class, () -> technician(null, skills, 1, 3));
        assertThrows(InvalidInputException.class,
                () -> new Administrator("A1", "Ann", "ann@corp.com", Role.TECHNICIAN));
        assertThrows(InvalidInputException.class,
                () -> new Administrator("A1", "Ann", "ann@corp.com", Role.TEAM_LEAD));
        assertThrows(InvalidInputException.class,
                () -> new Administrator("A1", "Ann", "ann@corp.com", Role.EMPLOYEE));
        assertEquals(Role.TECHNICIAN, technician(Role.TECHNICIAN, skills, 1, 3).getRole());
        assertEquals(Role.TEAM_LEAD, technician(Role.TEAM_LEAD, skills, 1, 3).getRole());
        assertEquals(Role.ADMINISTRATOR, new Administrator("A1", "Ann", "ann@corp.com", Role.ADMINISTRATOR).getRole());
        assertEquals(Role.IT_MANAGER, new Administrator("A2", "Ian", "ian@corp.com", Role.IT_MANAGER).getRole());
    }

    @Test
    void technician_invalidSkillsOrLimits_throwsInvalidInput() {
        assertThrows(InvalidInputException.class, () -> technician(Role.TECHNICIAN, null, 1, 3));
        assertThrows(InvalidInputException.class, () -> technician(Role.TECHNICIAN, Map.of("NETWORK", 0), 1, 3));
        assertThrows(InvalidInputException.class, () -> technician(Role.TECHNICIAN, Map.of("NETWORK", 6), 1, 3));
        assertThrows(InvalidInputException.class, () -> technician(Role.TECHNICIAN, Map.of(" ", 3), 1, 3));
        Map<String, Integer> nullKey = new HashMap<>();
        nullKey.put(null, 3);
        assertThrows(InvalidInputException.class, () -> technician(Role.TECHNICIAN, nullKey, 1, 3));
        Map<String, Integer> nullValue = new HashMap<>();
        nullValue.put("NETWORK", null);
        assertThrows(InvalidInputException.class, () -> technician(Role.TECHNICIAN, nullValue, 1, 3));
        assertThrows(InvalidInputException.class, () -> technician(Role.TECHNICIAN, Map.of(), -1, 3));
        assertThrows(InvalidInputException.class, () -> technician(Role.TECHNICIAN, Map.of(), 1, 0));
        // boundaries accepted: empty map, skill 1 and 5, 0 years, max 1
        technician(Role.TECHNICIAN, Map.of(), 0, 1);
        technician(Role.TECHNICIAN, Map.of("A", 1, "B", 5), 0, 1);
    }

    @Test
    void skillLevels_returnedMapIsUnmodifiableAndDetachedFromInput() {
        Map<String, Integer> source = new HashMap<>();
        source.put("NETWORK", 3);
        Technician t = technician(Role.TECHNICIAN, source, 5, 3);

        assertThrows(UnsupportedOperationException.class, () -> t.getSkillLevels().put("DATABASE", 2));
        assertThrows(UnsupportedOperationException.class, () -> t.getSkillLevels().remove("NETWORK"));

        source.put("DATABASE", 4);
        assertFalse(t.hasSkill("DATABASE"));
        assertEquals(3, t.skillLevelFor("NETWORK"));
        assertEquals(0, t.skillLevelFor("CLOUD"));
        assertTrue(t.hasSkill("NETWORK"));
        assertFalse(t.hasSkill("CLOUD"));
    }

    @Test
    void isAssignable_requiresActiveAndAvailable() {
        Technician t = technician(Role.TECHNICIAN, Map.of("NETWORK", 3), 5, 3);
        assertTrue(t.isAssignable());

        t.markUnavailable();
        assertFalse(t.isAvailable());
        assertFalse(t.isAssignable());

        t.markAvailable();
        assertTrue(t.isAssignable());

        t.deactivate();
        assertFalse(t.isAssignable());

        t.activate();
        assertTrue(t.isAssignable());
    }

    @Test
    void isOverloaded_boundaryAtMaxActiveIncidents() {
        Technician t = technician(Role.TECHNICIAN, Map.of(), 0, 3);
        assertFalse(t.isOverloaded(0));
        assertFalse(t.isOverloaded(2));
        assertTrue(t.isOverloaded(3));
        assertTrue(t.isOverloaded(4));
    }

    @Test
    void deactivateAndActivate_toggleActiveFlag() {
        Employee e = employee("E1");
        e.deactivate();
        assertFalse(e.isActive());
        e.activate();
        assertTrue(e.isActive());
    }

    @Test
    void equalsAndHashCode_byIdAndRuntimeClass() {
        Employee a = employee("X1");
        Employee b = new Employee("X1", "Other", "other@corp.com", "HR");
        Administrator admin = new Administrator("X1", "Ann", "ann@corp.com", Role.ADMINISTRATOR);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, employee("X2"));
        assertNotEquals(a, admin);
        assertNotEquals(a, null);
        assertEquals("ADMINISTRATOR X1", admin.toString());
    }
}
