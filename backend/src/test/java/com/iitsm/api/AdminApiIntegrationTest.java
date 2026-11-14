package com.iitsm.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.iitsm.application.security.TokenIssuer;
import com.iitsm.domain.model.BusinessCriticality;
import com.iitsm.domain.model.Employee;
import com.iitsm.domain.model.ItService;
import com.iitsm.domain.model.Role;
import com.iitsm.domain.model.Technician;
import com.iitsm.domain.model.User;
import com.iitsm.domain.repository.ItServiceRepository;
import com.iitsm.domain.repository.UserRepository;

/**
 * A7 end-to-end tests of the administrator API over real HTTP on a random port, on their own in-memory database.
 * Tokens are minted by the application's own TokenIssuer for users saved through the real repositories.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:a7-admin-api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "iitsm.bootstrap.admin.id=A7-ADMIN",
                "iitsm.bootstrap.admin.name=A7 Admin",
                "iitsm.bootstrap.admin.email=a7-admin@corp.com",
                "iitsm.bootstrap.admin.password=A7-Bootstrap-Pass-1"
        })
@ActiveProfiles("test")
class AdminApiIntegrationTest {

    private static final String PASSWORD = "Admin-Made-Pass-123";
    private static final String SERVICE_ID = "A7-SVC1";

    private final HttpClient http = HttpClient.newHttpClient();
    private final Map<String, String> tokens = new HashMap<>();

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private UserRepository users;

    @Autowired
    private ItServiceRepository services;

    @Autowired
    private TokenIssuer tokenIssuer;

    @BeforeEach
    void fixture() {
        saveIfMissing(new Employee("A7-E1", "Eve", "a7-e1@corp.com", "Finance"));
        saveIfMissing(new Technician("A7-T1", "Tia", "a7-t1@corp.com", Role.TECHNICIAN, Map.of("NETWORK", 3), 5, 50));
        if (services.findById(SERVICE_ID).isEmpty()) {
            services.save(new ItService(SERVICE_ID, "Payments", BusinessCriticality.HIGH));
        }
        for (String id : new String[] {"A7-ADMIN", "A7-E1", "A7-T1"}) {
            tokens.put(id, tokenIssuer.issue(users.findById(id).orElseThrow()).value());
        }
    }

    private void saveIfMissing(User user) {
        if (users.findById(user.getId()).isEmpty()) {
            users.save(user);
        }
    }

    // ---------- helpers ----------

    private HttpResponse<String> call(String method, String path, String userId, String json) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, json == null ? BodyPublishers.noBody() : BodyPublishers.ofString(json));
        if (json != null) {
            builder.header("Content-Type", "application/json");
        }
        if (userId != null) {
            builder.header("Authorization", "Bearer " + tokens.get(userId));
        }
        return http.send(builder.build(), BodyHandlers.ofString());
    }

    private static String field(String body, String name) {
        Matcher m = Pattern.compile("\"" + name + "\"\\s*:\\s*\"([^\"]*)\"").matcher(body);
        return m.find() ? m.group(1) : null;
    }

    private void assertError(HttpResponse<String> r, int status, String code) {
        assertEquals(status, r.statusCode(), r.body());
        assertEquals(code, field(r.body(), "code"), r.body());
        assertNotNull(field(r.body(), "timestamp"), r.body());
    }

    private static String employeeJson(String id) {
        return "{\"id\":\"" + id + "\",\"name\":\"Made " + id + "\",\"email\":\"" + id.toLowerCase()
                + "@corp.com\",\"role\":\"EMPLOYEE\",\"department\":\"Legal\",\"password\":\"" + PASSWORD + "\"}";
    }

    private static String technicianJson(String id, String skillsJson) {
        return "{\"id\":\"" + id + "\",\"name\":\"Tech " + id + "\",\"email\":\"" + id.toLowerCase()
                + "@corp.com\",\"role\":\"TECHNICIAN\",\"skillLevels\":" + skillsJson
                + ",\"yearsOfExperience\":4,\"maxActiveIncidents\":50,\"password\":\"" + PASSWORD + "\"}";
    }

    private static String incidentJson() {
        return "{\"title\":\"VPN down\",\"description\":\"Cannot connect to VPN\",\"serviceId\":\"" + SERVICE_ID
                + "\",\"categoryId\":\"NETWORK\",\"categorySource\":\"MANUAL\",\"impact\":\"HIGH\","
                + "\"urgency\":\"HIGH\",\"affectedUsers\":5}";
    }

    // ---------- authentication / authorization ----------

    @Test
    void adminEndpoints_withoutToken_return401() throws Exception {
        assertEquals(401, call("GET", "/api/admin/users", null, null).statusCode());
        assertEquals(401, call("GET", "/api/admin/technicians", null, null).statusCode());
        assertEquals(401, call("GET", "/api/admin/assignment-strategy", null, null).statusCode());
    }

    @Test
    void adminEndpoints_nonAdministrator_return403OnEveryEndpoint() throws Exception {
        for (String actor : List.of("A7-E1", "A7-T1")) {
            assertError(call("GET", "/api/admin/users", actor, null), 403, "UNAUTHORIZED_OPERATION");
            assertError(call("POST", "/api/admin/users", actor, employeeJson("A7-DENIED")), 403,
                    "UNAUTHORIZED_OPERATION");
            assertError(call("GET", "/api/admin/users/A7-E1", actor, null), 403, "UNAUTHORIZED_OPERATION");
            assertError(call("PUT", "/api/admin/users/A7-E1", actor, "{\"name\":\"Hacked\"}"), 403,
                    "UNAUTHORIZED_OPERATION");
            assertError(call("GET", "/api/admin/technicians", actor, null), 403, "UNAUTHORIZED_OPERATION");
            assertError(call("PUT", "/api/admin/technicians/A7-T1/availability", actor, "{\"available\":false}"),
                    403, "UNAUTHORIZED_OPERATION");
            assertError(call("GET", "/api/admin/assignment-strategy", actor, null), 403, "UNAUTHORIZED_OPERATION");
            assertError(call("PUT", "/api/admin/assignment-strategy", actor, "{\"strategy\":\"load-based\"}"), 403,
                    "UNAUTHORIZED_OPERATION");
        }

        assertTrue(users.findById("A7-DENIED").isEmpty());
        assertEquals("Eve", users.findById("A7-E1").orElseThrow().getName());
        assertTrue(((Technician) users.findById("A7-T1").orElseThrow()).isAvailable());
    }

    // ---------- users ----------

    @Test
    void admin_createsAnEmployee_withoutAnyPasswordInTheResponse_andTheNewUserCanLogIn() throws Exception {
        HttpResponse<String> created = call("POST", "/api/admin/users", "A7-ADMIN", employeeJson("A7-NEW1"));

        assertEquals(201, created.statusCode(), created.body());
        assertEquals("A7-NEW1", field(created.body(), "id"));
        assertEquals("EMPLOYEE", field(created.body(), "role"));
        assertEquals("Legal", field(created.body(), "department"));
        assertFalse(created.body().toLowerCase().contains("password"), created.body());
        assertFalse(created.body().contains(PASSWORD), created.body());
        assertFalse(created.body().contains("$2"), created.body());

        HttpResponse<String> login = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port
                        + "/api/auth/login")).header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString("{\"id\":\"A7-NEW1\",\"password\":\"" + PASSWORD + "\"}")).build(),
                BodyHandlers.ofString());
        assertEquals(200, login.statusCode(), login.body());
        assertNotNull(field(login.body(), "accessToken"));
    }

    @Test
    void admin_createsEveryRole_andListingShowsThemWithoutSecrets() throws Exception {
        assertEquals(201, call("POST", "/api/admin/users", "A7-ADMIN", technicianJson("A7-TECH1", "{\"NETWORK\":3}"))
                .statusCode());
        assertEquals(201, call("POST", "/api/admin/users", "A7-ADMIN",
                "{\"id\":\"A7-LEAD1\",\"name\":\"Lead\",\"email\":\"a7-lead1@corp.com\",\"role\":\"TEAM_LEAD\","
                        + "\"skillLevels\":{},\"yearsOfExperience\":9,\"maxActiveIncidents\":5,\"password\":\""
                        + PASSWORD + "\"}").statusCode());
        assertEquals(201, call("POST", "/api/admin/users", "A7-ADMIN",
                "{\"id\":\"A7-ADM2\",\"name\":\"Admin Two\",\"email\":\"a7-adm2@corp.com\","
                        + "\"role\":\"ADMINISTRATOR\",\"password\":\"" + PASSWORD + "\"}").statusCode());
        assertEquals(201, call("POST", "/api/admin/users", "A7-ADMIN",
                "{\"id\":\"A7-MGR1\",\"name\":\"Manager\",\"email\":\"a7-mgr1@corp.com\","
                        + "\"role\":\"IT_MANAGER\",\"password\":\"" + PASSWORD + "\"}").statusCode());

        HttpResponse<String> list = call("GET", "/api/admin/users", "A7-ADMIN", null);

        assertEquals(200, list.statusCode(), list.body());
        for (String role : List.of("TECHNICIAN", "TEAM_LEAD", "ADMINISTRATOR", "IT_MANAGER")) {
            assertTrue(list.body().contains("\"role\":\"" + role + "\""), role + " missing: " + list.body());
        }
        assertFalse(list.body().toLowerCase().contains("password"), list.body());
        assertFalse(list.body().contains("$2"), list.body());
    }

    @Test
    void admin_createUser_duplicateInvalidAndUnknownCategory_returnTheMappedErrors() throws Exception {
        assertEquals(201, call("POST", "/api/admin/users", "A7-ADMIN", employeeJson("A7-DUP1")).statusCode());

        assertError(call("POST", "/api/admin/users", "A7-ADMIN", employeeJson("A7-DUP1")), 400, "INVALID_INPUT");
        assertError(call("POST", "/api/admin/users", "A7-ADMIN", employeeJson("A7-BADMAIL").replace("@corp.com", "")),
                400, "INVALID_INPUT");
        assertError(call("POST", "/api/admin/users", "A7-ADMIN",
                employeeJson("A7-SHORT").replace(PASSWORD, "short")), 400, "INVALID_INPUT");
        assertError(call("POST", "/api/admin/users", "A7-ADMIN",
                employeeJson("A7-ROLE").replace("EMPLOYEE", "SUPERUSER")), 400, "INVALID_INPUT");
        assertError(call("POST", "/api/admin/users", "A7-ADMIN",
                technicianJson("A7-NOCAT", "{\"NO_SUCH_CATEGORY\":3}")), 404, "CATEGORY_NOT_FOUND");
        assertError(call("GET", "/api/admin/users/A7-NO-SUCH-USER", "A7-ADMIN", null), 404, "USER_NOT_FOUND");

        assertTrue(users.findById("A7-BADMAIL").isEmpty());
        assertTrue(users.findById("A7-NOCAT").isEmpty());
    }

    @Test
    void admin_updatesTechnicianSkills_theOldSkillsAreReplacedAndPersisted() throws Exception {
        assertEquals(201, call("POST", "/api/admin/users", "A7-ADMIN",
                technicianJson("A7-SKILL1", "{\"NETWORK\":3,\"DATABASE\":2}")).statusCode());

        HttpResponse<String> updated = call("PUT", "/api/admin/users/A7-SKILL1", "A7-ADMIN",
                "{\"skillLevels\":{\"CLOUD\":5},\"maxActiveIncidents\":7}");

        assertEquals(200, updated.statusCode(), updated.body());
        assertTrue(updated.body().contains("\"CLOUD\":5"), updated.body());
        assertFalse(updated.body().contains("NETWORK"), updated.body());

        // read back from the database: the removed skills must really be gone
        HttpResponse<String> reloaded = call("GET", "/api/admin/users/A7-SKILL1", "A7-ADMIN", null);
        assertTrue(reloaded.body().contains("\"CLOUD\":5"), reloaded.body());
        assertFalse(reloaded.body().contains("NETWORK"), reloaded.body());
        assertFalse(reloaded.body().contains("DATABASE"), reloaded.body());
        assertTrue(reloaded.body().contains("\"maxActiveIncidents\":7"), reloaded.body());
        assertEquals("TECHNICIAN", field(reloaded.body(), "role"));
    }

    @Test
    void admin_deactivatedUser_cannotLogInAnymore() throws Exception {
        assertEquals(201, call("POST", "/api/admin/users", "A7-ADMIN", employeeJson("A7-OFF1")).statusCode());

        HttpResponse<String> updated = call("PUT", "/api/admin/users/A7-OFF1", "A7-ADMIN", "{\"active\":false}");

        assertEquals(200, updated.statusCode(), updated.body());
        assertTrue(updated.body().contains("\"active\":false"), updated.body());
        HttpResponse<String> login = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port
                        + "/api/auth/login")).header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString("{\"id\":\"A7-OFF1\",\"password\":\"" + PASSWORD + "\"}")).build(),
                BodyHandlers.ofString());
        assertEquals(401, login.statusCode(), login.body());
    }

    // ---------- technicians ----------

    @Test
    void admin_setsTechnicianAvailability_andListsTechnicians() throws Exception {
        try {
            HttpResponse<String> off = call("PUT", "/api/admin/technicians/A7-T1/availability", "A7-ADMIN",
                    "{\"available\":false}");

            assertEquals(200, off.statusCode(), off.body());
            assertTrue(off.body().contains("\"available\":false"), off.body());
            assertTrue(off.body().contains("\"assignable\":false"), off.body());
            assertFalse(((Technician) users.findById("A7-T1").orElseThrow()).isAvailable());

            HttpResponse<String> list = call("GET", "/api/admin/technicians", "A7-ADMIN", null);
            assertEquals(200, list.statusCode(), list.body());
            assertTrue(list.body().contains("\"id\":\"A7-T1\""), list.body());
            assertFalse(list.body().toLowerCase().contains("password"), list.body());
        } finally {
            call("PUT", "/api/admin/technicians/A7-T1/availability", "A7-ADMIN", "{\"available\":true}");
        }
        assertTrue(((Technician) users.findById("A7-T1").orElseThrow()).isAvailable());
    }

    @Test
    void admin_availability_missingFlagNonTechnicianOrUnknown_returnTheMappedErrors() throws Exception {
        assertError(call("PUT", "/api/admin/technicians/A7-T1/availability", "A7-ADMIN", "{}"), 400, "INVALID_INPUT");
        assertError(call("PUT", "/api/admin/technicians/A7-E1/availability", "A7-ADMIN", "{\"available\":false}"),
                400, "INVALID_INPUT");
        assertError(call("PUT", "/api/admin/technicians/A7-NO-SUCH/availability", "A7-ADMIN",
                "{\"available\":false}"), 404, "USER_NOT_FOUND");
    }

    // ---------- assignment strategy ----------

    @Test
    void admin_selectsStrategy_andUnknownNamesAreRejected() throws Exception {
        try {
            HttpResponse<String> current = call("GET", "/api/admin/assignment-strategy", "A7-ADMIN", null);
            assertEquals(200, current.statusCode(), current.body());
            assertEquals("skill-based", field(current.body(), "selected"), current.body());
            assertTrue(current.body().contains("load-based"), current.body());

            HttpResponse<String> switched = call("PUT", "/api/admin/assignment-strategy", "A7-ADMIN",
                    "{\"strategy\":\"load-based\"}");
            assertEquals(200, switched.statusCode(), switched.body());
            assertEquals("load-based", field(switched.body(), "selected"), switched.body());

            assertError(call("PUT", "/api/admin/assignment-strategy", "A7-ADMIN", "{\"strategy\":\"round-robin\"}"),
                    400, "INVALID_INPUT");
            assertEquals("load-based",
                    field(call("GET", "/api/admin/assignment-strategy", "A7-ADMIN", null).body(), "selected"));
        } finally {
            call("PUT", "/api/admin/assignment-strategy", "A7-ADMIN", "{\"strategy\":\"skill-based\"}");
        }
    }

    @Test
    void switchingTheStrategy_changesWhoAutomaticAssignmentPicks() throws Exception {
        try {
            // only the two technicians created here may receive the work
            call("PUT", "/api/admin/technicians/A7-T1/availability", "A7-ADMIN", "{\"available\":false}");
            assertEquals(201, call("POST", "/api/admin/users", "A7-ADMIN",
                    technicianJson("A7-LOW", "{\"NETWORK\":3}")).statusCode());
            assertEquals(201, call("POST", "/api/admin/users", "A7-ADMIN",
                    technicianJson("A7-HIGH", "{\"NETWORK\":5}")).statusCode());
            call("PUT", "/api/admin/assignment-strategy", "A7-ADMIN", "{\"strategy\":\"skill-based\"}");

            // skill-based: the most skilled technician gets every network incident
            HttpResponse<String> first = call("POST", "/api/incidents", "A7-E1", incidentJson());
            HttpResponse<String> second = call("POST", "/api/incidents", "A7-E1", incidentJson());
            assertEquals(201, first.statusCode(), first.body());
            assertEquals("A7-HIGH", field(first.body(), "assignedTechnicianId"), first.body());
            assertEquals("A7-HIGH", field(second.body(), "assignedTechnicianId"), second.body());

            // load-based: A7-HIGH now carries two incidents and A7-LOW none
            assertEquals(200, call("PUT", "/api/admin/assignment-strategy", "A7-ADMIN",
                    "{\"strategy\":\"load-based\"}").statusCode());
            HttpResponse<String> third = call("POST", "/api/incidents", "A7-E1", incidentJson());
            assertEquals("A7-LOW", field(third.body(), "assignedTechnicianId"), third.body());
        } finally {
            call("PUT", "/api/admin/assignment-strategy", "A7-ADMIN", "{\"strategy\":\"skill-based\"}");
            call("PUT", "/api/admin/technicians/A7-T1/availability", "A7-ADMIN", "{\"available\":true}");
        }
    }
}
