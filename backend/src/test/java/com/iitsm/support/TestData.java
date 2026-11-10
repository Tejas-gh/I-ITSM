package com.iitsm.support;

import com.iitsm.domain.incident.IncidentDraft;
import com.iitsm.domain.model.Administrator;
import com.iitsm.domain.model.BusinessCriticality;
import com.iitsm.domain.model.Category;
import com.iitsm.domain.model.CategorySource;
import com.iitsm.domain.model.Employee;
import com.iitsm.domain.model.Impact;
import com.iitsm.domain.model.ItService;
import com.iitsm.domain.model.PriorityLevel;
import com.iitsm.domain.model.Role;
import com.iitsm.domain.model.SlaPolicy;
import com.iitsm.domain.model.Technician;
import com.iitsm.domain.model.Urgency;
import com.iitsm.domain.model.User;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Common factory methods providing standard test entities and data.
 */
public final class TestData {

    private TestData() {
    }

    public static Employee employee(String id) {
        return new Employee(id, "Employee " + id, id.toLowerCase() + "@corp.com", "Finance");
    }

    public static Technician technician(String id, int maxActive) {
        return new Technician(id, "Tech " + id, id.toLowerCase() + "@corp.com",
                Role.TECHNICIAN, Map.of("NETWORK", 3, "DATABASE", 2), 5, maxActive);
    }

    public static Technician teamLead(String id) {
        return new Technician(id, "Lead " + id, id.toLowerCase() + "@corp.com",
                Role.TEAM_LEAD, Map.of("NETWORK", 3, "DATABASE", 2), 5, 5);
    }

    public static Administrator admin(String id) {
        return new Administrator(id, "Admin " + id, id.toLowerCase() + "@corp.com", Role.ADMINISTRATOR);
    }

    public static ItService service(String id, BusinessCriticality c) {
        return new ItService(id, "Service " + id, c);
    }

    public static Category category(String id) {
        return new Category(id, id);
    }

    public static List<Category> defaultCategories() {
        return List.of(
                new Category("HARDWARE", "HARDWARE"),
                new Category("SOFTWARE", "SOFTWARE"),
                new Category("NETWORK", "NETWORK"),
                new Category("DATABASE", "DATABASE"),
                new Category("CLOUD", "CLOUD"),
                new Category("SECURITY", "SECURITY"),
                new Category("AUTHENTICATION", "AUTHENTICATION"),
                new Category("APPLICATION", "APPLICATION"),
                new Category("STORAGE", "STORAGE")
        );
    }

    public static List<SlaPolicy> defaultSlaPolicies() {
        return List.of(
                new SlaPolicy(null, PriorityLevel.P1, Duration.ofMinutes(5), Duration.ofMinutes(30)),
                new SlaPolicy(null, PriorityLevel.P2, Duration.ofMinutes(15), Duration.ofHours(2)),
                new SlaPolicy(null, PriorityLevel.P3, Duration.ofHours(1), Duration.ofHours(8)),
                new SlaPolicy(null, PriorityLevel.P4, Duration.ofHours(4), Duration.ofHours(24))
        );
    }

    public static IncidentDraft draft(User reporter, ItService service, Category category,
                                      Impact impact, Urgency urgency, int affectedUsers) {
        return new IncidentDraft(
                "Test Incident Title",
                "Test incident description details",
                reporter,
                service,
                category,
                null,
                CategorySource.MANUAL,
                impact,
                urgency,
                affectedUsers
        );
    }
}

