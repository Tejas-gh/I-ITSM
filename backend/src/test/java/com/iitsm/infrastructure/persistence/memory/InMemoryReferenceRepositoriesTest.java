package com.iitsm.infrastructure.persistence.memory;

import com.iitsm.domain.exception.InvalidInputException;
import com.iitsm.domain.model.Administrator;
import com.iitsm.domain.model.BusinessCriticality;
import com.iitsm.domain.model.Category;
import com.iitsm.domain.model.Employee;
import com.iitsm.domain.model.ItService;
import com.iitsm.domain.model.PriorityLevel;
import com.iitsm.domain.model.Role;
import com.iitsm.domain.model.SlaPolicy;
import com.iitsm.domain.model.Technician;
import com.iitsm.domain.model.User;
import com.iitsm.support.TestData;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryReferenceRepositoriesTest {

    // ===== UserRepository tests =====

    @Test
    void userRepository_saveFindAndFindAll_preservesInsertionOrder() {
        InMemoryUserRepository repo = new InMemoryUserRepository();
        Employee emp = TestData.employee("U1");
        Technician tech = TestData.technician("U2", 3);
        Administrator admin = TestData.admin("U3");

        repo.save(emp);
        repo.save(tech);
        repo.save(admin);

        assertEquals(3, repo.findAll().size());
        assertEquals("U1", repo.findAll().get(0).getId());
        assertEquals("U2", repo.findAll().get(1).getId());
        assertEquals("U3", repo.findAll().get(2).getId());

        assertSame(emp, repo.findById("U1").orElseThrow());
        assertTrue(repo.findById("UNKNOWN").isEmpty());
    }

    @Test
    void userRepository_findAllTechnicians_returnsOnlyTechniciansAndTeamLeads() {
        InMemoryUserRepository repo = new InMemoryUserRepository();
        Employee emp = TestData.employee("U1");
        Technician tech = TestData.technician("U2", 3);
        Technician lead = TestData.teamLead("U3");
        Administrator admin = TestData.admin("U4");

        repo.save(emp);
        repo.save(tech);
        repo.save(lead);
        repo.save(admin);

        List<Technician> technicians = repo.findAllTechnicians();
        assertEquals(2, technicians.size());
        assertEquals("U2", technicians.get(0).getId());
        assertEquals("U3", technicians.get(1).getId());
    }

    @Test
    void userRepository_nullArguments_throwInvalidInput() {
        InMemoryUserRepository repo = new InMemoryUserRepository();
        assertThrows(InvalidInputException.class, () -> repo.save(null));
        assertThrows(InvalidInputException.class, () -> repo.findById(null));
    }

    // ===== ItServiceRepository tests =====

    @Test
    void itServiceRepository_saveFindAndFindAll_operatesCorrectly() {
        InMemoryItServiceRepository repo = new InMemoryItServiceRepository();
        ItService s1 = TestData.service("SVC1", BusinessCriticality.HIGH);
        ItService s2 = TestData.service("SVC2", BusinessCriticality.CRITICAL);

        repo.save(s1);
        repo.save(s2);

        assertEquals(2, repo.findAll().size());
        assertSame(s1, repo.findById("SVC1").orElseThrow());
        assertTrue(repo.findById("SVC_NONE").isEmpty());

        // Upsert test
        ItService s1Updated = new ItService("SVC1", "Updated Name", BusinessCriticality.LOW);
        repo.save(s1Updated);
        assertEquals(2, repo.findAll().size());
        assertEquals("Updated Name", repo.findById("SVC1").orElseThrow().getName());
    }

    @Test
    void itServiceRepository_nullArguments_throwInvalidInput() {
        InMemoryItServiceRepository repo = new InMemoryItServiceRepository();
        assertThrows(InvalidInputException.class, () -> repo.save(null));
        assertThrows(InvalidInputException.class, () -> repo.findById(null));
    }

    // ===== CategoryRepository tests =====

    @Test
    void categoryRepository_saveFindAllAndFindAllActive_filtersInactive() {
        InMemoryCategoryRepository repo = new InMemoryCategoryRepository();
        Category c1 = TestData.category("NETWORK");
        Category c2 = TestData.category("DATABASE");
        Category c3 = TestData.category("LEGACY");
        c3.deactivate();

        repo.save(c1);
        repo.save(c2);
        repo.save(c3);

        assertEquals(3, repo.findAll().size());
        List<Category> active = repo.findAllActive();
        assertEquals(2, active.size());
        assertEquals("NETWORK", active.get(0).getId());
        assertEquals("DATABASE", active.get(1).getId());

        assertSame(c1, repo.findById("NETWORK").orElseThrow());
        assertTrue(repo.findById("UNKNOWN").isEmpty());
    }

    @Test
    void categoryRepository_nullArguments_throwInvalidInput() {
        InMemoryCategoryRepository repo = new InMemoryCategoryRepository();
        assertThrows(InvalidInputException.class, () -> repo.save(null));
        assertThrows(InvalidInputException.class, () -> repo.findById(null));
    }

    // ===== SlaPolicyRepository tests =====

    @Test
    void slaPolicyRepository_saveAndFindDefault_resolvesDefaultPolicies() {
        InMemorySlaPolicyRepository repo = new InMemorySlaPolicyRepository();
        SlaPolicy defaultP1 = new SlaPolicy(null, PriorityLevel.P1, Duration.ofMinutes(5), Duration.ofMinutes(30));
        SlaPolicy defaultP2 = new SlaPolicy(null, PriorityLevel.P2, Duration.ofMinutes(15), Duration.ofHours(2));

        repo.save(defaultP1);
        repo.save(defaultP2);

        assertEquals(2, repo.findAll().size());
        assertSame(defaultP1, repo.findDefault(PriorityLevel.P1).orElseThrow());
        assertSame(defaultP2, repo.findDefault(PriorityLevel.P2).orElseThrow());
        assertTrue(repo.findDefault(PriorityLevel.P3).isEmpty());
    }

    @Test
    void slaPolicyRepository_saveAndFindOverride_resolvesServiceOverrides() {
        InMemorySlaPolicyRepository repo = new InMemorySlaPolicyRepository();
        SlaPolicy overrideP1 = new SlaPolicy("SVC1", PriorityLevel.P1, Duration.ofMinutes(2), Duration.ofMinutes(10));
        repo.save(overrideP1);

        Optional<SlaPolicy> found = repo.findOverride("SVC1", PriorityLevel.P1);
        assertTrue(found.isPresent());
        assertSame(overrideP1, found.get());

        assertTrue(repo.findOverride("SVC1", PriorityLevel.P2).isEmpty());
        assertTrue(repo.findOverride("OTHER_SVC", PriorityLevel.P1).isEmpty());
    }

    @Test
    void slaPolicyRepository_replaceSameKey_overwritesExistingPolicy() {
        InMemorySlaPolicyRepository repo = new InMemorySlaPolicyRepository();
        SlaPolicy original = new SlaPolicy("SVC1", PriorityLevel.P1, Duration.ofMinutes(5), Duration.ofMinutes(30));
        SlaPolicy updated = new SlaPolicy("SVC1", PriorityLevel.P1, Duration.ofMinutes(2), Duration.ofMinutes(10));

        repo.save(original);
        repo.save(updated);

        assertEquals(1, repo.findAll().size());
        assertEquals(Duration.ofMinutes(2), repo.findOverride("SVC1", PriorityLevel.P1).orElseThrow().responseTarget());
    }

    @Test
    void slaPolicyRepository_nullArguments_throwInvalidInput() {
        InMemorySlaPolicyRepository repo = new InMemorySlaPolicyRepository();
        assertThrows(InvalidInputException.class, () -> repo.save(null));
        assertThrows(InvalidInputException.class, () -> repo.findDefault(null));
        assertThrows(InvalidInputException.class, () -> repo.findOverride(null, PriorityLevel.P1));
        assertThrows(InvalidInputException.class, () -> repo.findOverride("SVC1", null));
    }

    // ===== InMemoryIncidentIdGenerator tests =====

    @Test
    void incidentIdGenerator_defaultStart_generatesINC1001AndIncrements() {
        InMemoryIncidentIdGenerator gen = new InMemoryIncidentIdGenerator();
        assertEquals("INC1001", gen.nextIncidentId());
        assertEquals("INC1002", gen.nextIncidentId());
        assertEquals("INC1003", gen.nextIncidentId());
    }

    @Test
    void incidentIdGenerator_customStart_startsFromProvidedNumber() {
        InMemoryIncidentIdGenerator gen = new InMemoryIncidentIdGenerator(5000);
        assertEquals("INC5000", gen.nextIncidentId());
        assertEquals("INC5001", gen.nextIncidentId());
    }

    @Test
    void incidentIdGenerator_invalidStart_throwsInvalidInput() {
        assertThrows(InvalidInputException.class, () -> new InMemoryIncidentIdGenerator(0));
        assertThrows(InvalidInputException.class, () -> new InMemoryIncidentIdGenerator(-10));
    }

    // ===== Unmodifiable list tests =====

    @Test
    void referenceRepositories_returnedLists_areUnmodifiable() {
        InMemoryUserRepository userRepo = new InMemoryUserRepository();
        userRepo.save(TestData.employee("U1"));
        assertThrows(UnsupportedOperationException.class, () -> userRepo.findAll().add(TestData.employee("U2")));
        assertThrows(UnsupportedOperationException.class, () -> userRepo.findAllTechnicians().add(TestData.technician("T1", 5)));

        InMemoryCategoryRepository catRepo = new InMemoryCategoryRepository();
        catRepo.save(TestData.category("NETWORK"));
        assertThrows(UnsupportedOperationException.class, () -> catRepo.findAll().add(TestData.category("DATABASE")));
        assertThrows(UnsupportedOperationException.class, () -> catRepo.findAllActive().add(TestData.category("DATABASE")));

        InMemoryItServiceRepository svcRepo = new InMemoryItServiceRepository();
        svcRepo.save(TestData.service("SVC1", BusinessCriticality.HIGH));
        assertThrows(UnsupportedOperationException.class, () -> svcRepo.findAll().add(TestData.service("SVC2", BusinessCriticality.LOW)));

        InMemorySlaPolicyRepository slaRepo = new InMemorySlaPolicyRepository();
        slaRepo.save(new SlaPolicy(null, PriorityLevel.P1, Duration.ofMinutes(5), Duration.ofMinutes(30)));
        assertThrows(UnsupportedOperationException.class, () -> slaRepo.findAll().add(null));
    }
}

