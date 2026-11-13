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
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
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
 * A6 end-to-end tests of the incident API over real HTTP on a random port, on their own in-memory database.
 * Tokens are minted by the application's own TokenIssuer for users saved through the real repositories.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:a6-incident-api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "iitsm.bootstrap.admin.id=A6-ADMIN",
                "iitsm.bootstrap.admin.name=A6 Admin",
                "iitsm.bootstrap.admin.email=a6-admin@corp.com",
                "iitsm.bootstrap.admin.password=A6-Bootstrap-Pass-1"
        })
@ActiveProfiles("test")
class IncidentApiIntegrationTest {

    private static final String SERVICE_ID = "A6-SVC1";

    private final HttpClient http = HttpClient.newHttpClient();
    private final Map<String, String> tokens = new HashMap<>();

    @Value("${local.server.port}")
    private int port;

    @Autowired private UserRepository users;
    @Autowired private ItServiceRepository services;
    @Autowired private TokenIssuer tokenIssuer;

    @BeforeEach
    void fixture() {
        saveIfMissing(new Employee("A6-E1", "Eve", "a6-e1@corp.com", "Finance"));
        saveIfMissing(new Employee("A6-E2", "Evan", "a6-e2@corp.com", "Sales"));
        saveIfMissing(new Technician("A6-T1", "Tia", "a6-t1@corp.com", Role.TECHNICIAN, Map.of("NETWORK", 3), 5, 50));
        saveIfMissing(new Technician("A6-T2", "Tom", "a6-t2@corp.com", Role.TECHNICIAN, Map.of("NETWORK", 2), 5, 50));
        Technician unavailable =
                new Technician("A6-T3", "Una", "a6-t3@corp.com", Role.TECHNICIAN, Map.of("NETWORK", 1), 5, 50);
        unavailable.markUnavailable();
        saveIfMissing(unavailable);
        if (services.findById(SERVICE_ID).isEmpty()) {
            services.save(new ItService(SERVICE_ID, "Payments", BusinessCriticality.HIGH));
        }
        for (String id : new String[] {"A6-E1", "A6-E2", "A6-T1", "A6-T2", "A6-T3", "A6-ADMIN"}) {
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

    private static int count(String body, String regex) {
        Matcher m = Pattern.compile(regex).matcher(body);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }

    private static String createJson(String serviceId, String categoryId, String impact, String urgency, int users) {
        return "{\"title\":\"VPN down\",\"description\":\"Cannot connect to VPN\",\"serviceId\":\"" + serviceId
                + "\",\"categoryId\":\"" + categoryId + "\",\"categorySource\":\"MANUAL\",\"impact\":\"" + impact
                + "\",\"urgency\":\"" + urgency + "\",\"affectedUsers\":" + users + "}";
    }

    private static String text(String value) {
        return "{\"text\":\"" + value + "\"}";
    }

    /** Employee A6-E1 reports an incident (auto-assigned to a technician); returns the response body. */
    private String createIncident() throws Exception {
        HttpResponse<String> r = call("POST", "/api/incidents", "A6-E1",
                createJson(SERVICE_ID, "NETWORK", "HIGH", "HIGH", 5));
        assertEquals(201, r.statusCode(), r.body());
        return r.body();
    }

    private void assertError(HttpResponse<String> r, int status, String code) {
        assertEquals(status, r.statusCode(), r.body());
        assertTrue(r.body().contains("\"status\":" + status), r.body());
        assertEquals(code, field(r.body(), "code"), r.body());
        assertNotNull(field(r.body(), "timestamp"), r.body());
        assertNotNull(field(r.body(), "message"), r.body());
        assertTrue(r.body().contains("\"details\":[]"), r.body());
    }

    // ---------- security ----------

    @Test
    void incidents_withoutToken_returns401() throws Exception {
        assertEquals(401, call("GET", "/api/incidents", null, null).statusCode());
        assertEquals(401, call("POST", "/api/incidents", null, createJson(SERVICE_ID, "NETWORK", "HIGH", "HIGH", 1))
                .statusCode());
    }

    // ---------- create ----------

    @Test
    void create_asEmployee_returns201WithContractFields() throws Exception {
        String body = createIncident();

        assertTrue(field(body, "id").startsWith("INC"));
        assertEquals("VPN down", field(body, "title"));
        assertEquals("A6-E1", field(body, "reporterId"));
        assertEquals(SERVICE_ID, field(body, "serviceId"));
        assertEquals("NETWORK", field(body, "categoryId"));
        assertEquals("MANUAL", field(body, "categorySource"));
        assertEquals("HIGH", field(body, "impact"));
        assertEquals("P2", field(body, "priority"));
        assertTrue(body.contains("\"priorityScore\":27"), body);
        assertEquals("ASSIGNED", field(body, "status"));
        assertTrue(field(body, "assignedTechnicianId").matches("A6-T[12]"), body);
        assertTrue(field(body, "createdAt").matches("\\d{4}-\\d{2}-\\d{2}T.*"), body);
        assertNotNull(field(body, "responseDeadline"));
        assertNotNull(field(body, "resolutionDeadline"));
        assertTrue(body.contains("\"notes\":[]"), body);
        assertTrue(body.contains("\"resolution\":null"), body);
        assertTrue(body.contains("\"feedback\":null"), body);
        for (String excluded : new String[] {"closureNote", "closedById", "waitingSince", "pausedDuration",
                "assignedAt"}) {
            assertFalse(body.contains("\"" + excluded + "\""), excluded);
        }
    }

    @Test
    void create_asTechnician_returns403InContractFormat() throws Exception {
        assertError(call("POST", "/api/incidents", "A6-T1", createJson(SERVICE_ID, "NETWORK", "HIGH", "HIGH", 1)),
                403, "UNAUTHORIZED_OPERATION");
    }

    @Test
    void create_invalidInput_returns400() throws Exception {
        assertError(call("POST", "/api/incidents", "A6-E1", createJson(SERVICE_ID, "NETWORK", "HIGH", "HIGH", 0)),
                400, "INVALID_INPUT");
    }

    @Test
    void create_malformedJsonOrUnknownEnum_returns400InvalidInput() throws Exception {
        assertError(call("POST", "/api/incidents", "A6-E1", "{not json"), 400, "INVALID_INPUT");
        assertError(call("POST", "/api/incidents", "A6-E1", createJson(SERVICE_ID, "NETWORK", "HUGE", "HIGH", 1)),
                400, "INVALID_INPUT");
    }

    @Test
    void create_unknownServiceOrCategory_returns404() throws Exception {
        assertError(call("POST", "/api/incidents", "A6-E1", createJson("NO-SUCH", "NETWORK", "HIGH", "HIGH", 1)),
                404, "SERVICE_NOT_FOUND");
        assertError(call("POST", "/api/incidents", "A6-E1", createJson(SERVICE_ID, "NO_SUCH", "HIGH", "HIGH", 1)),
                404, "CATEGORY_NOT_FOUND");
    }

    @Test
    void ids_areUniqueAcrossCreations() throws Exception {
        String first = field(createIncident(), "id");
        String second = field(createIncident(), "id");
        assertTrue(!first.equals(second));
    }

    // ---------- read ----------

    @Test
    void get_visibilityNotFoundAndForeignIncident() throws Exception {
        String id = field(createIncident(), "id");

        HttpResponse<String> own = call("GET", "/api/incidents/" + id, "A6-E1", null);
        assertEquals(200, own.statusCode());
        assertEquals(id, field(own.body(), "id"));
        assertEquals(200, call("GET", "/api/incidents/" + id, "A6-ADMIN", null).statusCode());
        assertError(call("GET", "/api/incidents/" + id, "A6-E2", null), 403, "UNAUTHORIZED_OPERATION");
        assertError(call("GET", "/api/incidents/INC0000", "A6-E1", null), 404, "INCIDENT_NOT_FOUND");
    }

    // ---------- full lifecycle ----------

    @Test
    void lifecycle_createToClosedWithFeedback_followsTheStateMachineOverHttp() throws Exception {
        String created = createIncident();
        String id = field(created, "id");
        String tech = field(created, "assignedTechnicianId");
        String base = "/api/incidents/" + id;

        // wrong actors are rejected
        assertError(call("POST", base + "/acknowledge", "A6-E1", null), 403, "UNAUTHORIZED_OPERATION");
        assertError(call("POST", base + "/acknowledge", "A6-ADMIN", null), 403, "UNAUTHORIZED_OPERATION");

        HttpResponse<String> r = call("POST", base + "/acknowledge", tech, null);
        assertEquals(200, r.statusCode(), r.body());
        assertEquals("ACKNOWLEDGED", field(r.body(), "status"));
        assertNotNull(field(r.body(), "acknowledgedAt"));

        r = call("POST", base + "/start-work", tech, null);
        assertEquals("IN_PROGRESS", field(r.body(), "status"));

        r = call("POST", base + "/notes", tech, text("checking the gateway"));
        assertEquals(200, r.statusCode(), r.body());
        assertEquals(1, count(r.body(), "\"type\":\"INVESTIGATION\""));

        r = call("POST", base + "/request-info", tech, text("Which VPN client?"));
        assertEquals("WAITING_FOR_USER", field(r.body(), "status"));

        r = call("POST", base + "/respond-info", "A6-E1", text("Client 5.2"));
        assertEquals("IN_PROGRESS", field(r.body(), "status"));

        r = call("POST", base + "/additional-info", "A6-E1", text("Started this morning"));
        assertEquals(200, r.statusCode(), r.body());
        assertEquals(1, count(r.body(), "\"type\":\"ADDITIONAL_INFO\""));

        r = call("POST", base + "/resolve", tech, text("Restarted the gateway"));
        assertEquals("RESOLVED", field(r.body(), "status"));
        assertNotNull(field(r.body(), "resolvedAt"));
        assertEquals("Restarted the gateway", field(r.body(), "summary"));

        r = call("POST", base + "/verify", "A6-E1", null);
        assertEquals("VERIFIED", field(r.body(), "status"));

        r = call("POST", base + "/close", "A6-ADMIN", "{\"closureNote\":\"Confirmed fixed\"}");
        assertEquals("CLOSED", field(r.body(), "status"));
        assertNotNull(field(r.body(), "closedAt"));

        r = call("POST", base + "/feedback", "A6-E1", "{\"rating\":5,\"comment\":\"Quick fix\"}");
        assertEquals(200, r.statusCode(), r.body());
        assertTrue(r.body().contains("\"rating\":5"), r.body());
        assertEquals("Quick fix", field(r.body(), "comment"));

        // the persisted aggregate reads back whole
        r = call("GET", base, "A6-E1", null);
        assertEquals("CLOSED", field(r.body(), "status"));
        assertEquals(1, count(r.body(), "\"type\":\"INVESTIGATION\""));
        assertEquals(1, count(r.body(), "\"type\":\"INFO_REQUEST\""));
        assertEquals(1, count(r.body(), "\"type\":\"INFO_RESPONSE\""));
        assertEquals(1, count(r.body(), "\"type\":\"ADDITIONAL_INFO\""));
        assertFalse(r.body().contains("closureNote"), r.body());   // not part of the §U.1 response
    }

    @Test
    void rejectResolution_returnsToInProgress() throws Exception {
        String created = createIncident();
        String id = field(created, "id");
        String tech = field(created, "assignedTechnicianId");
        String base = "/api/incidents/" + id;
        call("POST", base + "/acknowledge", tech, null);
        call("POST", base + "/start-work", tech, null);
        call("POST", base + "/resolve", tech, text("Tried a fix"));

        HttpResponse<String> r = call("POST", base + "/reject-resolution", "A6-E1", null);

        assertEquals(200, r.statusCode(), r.body());
        assertEquals("IN_PROGRESS", field(r.body(), "status"));
        assertTrue(r.body().contains("\"resolution\":null"), r.body());
    }

    @Test
    void illegalTransitions_return409InvalidStateTransition() throws Exception {
        String created = createIncident();
        String id = field(created, "id");
        String tech = field(created, "assignedTechnicianId");
        String base = "/api/incidents/" + id;

        assertError(call("POST", base + "/close", "A6-ADMIN", "{}"), 409, "INVALID_STATE_TRANSITION");
        assertError(call("POST", base + "/resolve", tech, text("too early")), 409, "INVALID_STATE_TRANSITION");
        assertError(call("POST", base + "/assign", "A6-ADMIN", "{\"technicianId\":\"A6-T1\"}"),
                409, "INVALID_STATE_TRANSITION");
        assertError(call("POST", base + "/auto-assign", "A6-ADMIN", null), 409, "INVALID_STATE_TRANSITION");
        assertEquals("ASSIGNED", field(call("GET", base, "A6-E1", null).body(), "status"));   // nothing changed
    }

    // ---------- assignment ----------

    @Test
    void reassign_toOtherTechnician_changesAssignee_andRejectsSameOrUnavailable() throws Exception {
        String created = createIncident();
        String id = field(created, "id");
        String current = field(created, "assignedTechnicianId");
        String other = current.equals("A6-T1") ? "A6-T2" : "A6-T1";
        String base = "/api/incidents/" + id;

        assertError(call("POST", base + "/reassign", "A6-E1", "{\"technicianId\":\"" + other + "\"}"),
                403, "UNAUTHORIZED_OPERATION");
        assertError(call("POST", base + "/reassign", "A6-ADMIN", "{\"technicianId\":\"" + current + "\"}"),
                400, "INVALID_INPUT");
        assertError(call("POST", base + "/reassign", "A6-ADMIN", "{\"technicianId\":\"A6-T3\"}"),
                409, "TECHNICIAN_UNAVAILABLE");
        assertError(call("POST", base + "/reassign", "A6-ADMIN", "{\"technicianId\":\"NO-ONE\"}"),
                404, "USER_NOT_FOUND");

        HttpResponse<String> r = call("POST", base + "/reassign", "A6-ADMIN", "{\"technicianId\":\"" + other + "\"}");
        assertEquals(200, r.statusCode(), r.body());
        assertEquals(other, field(r.body(), "assignedTechnicianId"));
        assertEquals("ASSIGNED", field(r.body(), "status"));
    }

    // ---------- priority ----------

    @Test
    void priorityInputs_adminRecalculates_employeeForbidden() throws Exception {
        String id = field(createIncident(), "id");
        String path = "/api/incidents/" + id + "/priority-inputs";

        assertError(call("PUT", path, "A6-E1", "{\"impact\":\"LOW\",\"urgency\":\"LOW\"}"),
                403, "UNAUTHORIZED_OPERATION");
        HttpResponse<String> r = call("PUT", path, "A6-ADMIN", "{\"impact\":\"LOW\",\"urgency\":\"LOW\"}");

        assertEquals(200, r.statusCode(), r.body());
        assertEquals("P4", field(r.body(), "priority"));
        assertTrue(r.body().contains("\"priorityScore\":3"), r.body());
        assertEquals("LOW", field(r.body(), "impact"));
    }

    // ---------- suggest category ----------

    @Test
    void suggestCategory_returns200_andEmptyBodyWhenThereIsNoSuggestion() throws Exception {
        HttpResponse<String> r = call("POST", "/api/incidents/suggest-category", "A6-E1",
                "{\"title\":\"VPN down\",\"description\":\"cannot connect\"}");

        assertEquals(200, r.statusCode(), r.body());
        assertEquals("", r.body());
    }

    // ---------- search ----------

    @Test
    void list_appliesEachFilterAndRoleVisibility() throws Exception {
        String created = createIncident();
        String id = field(created, "id");
        String tech = field(created, "assignedTechnicianId");
        String otherTech = tech.equals("A6-T1") ? "A6-T2" : "A6-T1";
        String byId = "/api/incidents?incidentId=" + id;

        assertEquals(1, count(call("GET", byId, "A6-E1", null).body(), "\"id\":\"" + id + "\""));
        assertEquals(1, count(call("GET", byId, "A6-ADMIN", null).body(), "\"id\":\"" + id + "\""));
        assertEquals(1, count(call("GET", byId, tech, null).body(), "\"id\":\"" + id + "\""));
        assertEquals(0, count(call("GET", byId, "A6-E2", null).body(), "\"id\":\"" + id + "\""));
        assertEquals(0, count(call("GET", byId, otherTech, null).body(), "\"id\":\"" + id + "\""));

        String[][] matching = {
                {"status=ASSIGNED", "1"}, {"status=RESOLVED", "0"},
                {"priority=P2", "1"}, {"priority=P1", "0"},
                {"categoryId=NETWORK", "1"}, {"categoryId=DATABASE", "0"},
                {"serviceId=" + SERVICE_ID, "1"}, {"serviceId=OTHER", "0"},
                {"technicianId=" + tech, "1"}, {"technicianId=" + otherTech, "0"},
                {"createdFrom=" + Instant.now().minus(Duration.ofHours(1)), "1"},
                {"createdFrom=" + Instant.now().plus(Duration.ofHours(1)), "0"},
                {"createdTo=" + Instant.now().plus(Duration.ofHours(1)), "1"},
                {"createdTo=2000-01-01T00:00:00Z", "0"},
                {"slaStatus=ON_TRACK", "1"}, {"slaStatus=BREACHED", "0"}
        };
        for (String[] filter : matching) {
            String body = call("GET", byId + "&" + filter[0], "A6-ADMIN", null).body();
            assertEquals(Integer.parseInt(filter[1]), count(body, "\"id\":\"" + id + "\""), filter[0] + " -> " + body);
        }
    }

    @Test
    void list_withoutFilters_returnsVisibleIncidentsForTheCaller() throws Exception {
        String id = field(createIncident(), "id");

        HttpResponse<String> mine = call("GET", "/api/incidents", "A6-E1", null);
        assertEquals(200, mine.statusCode());
        assertTrue(mine.body().contains("\"id\":\"" + id + "\""));
        assertFalse(call("GET", "/api/incidents", "A6-E2", null).body().contains("\"id\":\"" + id + "\""));
    }

    @Test
    void list_invalidFilterValues_return400InvalidInput() throws Exception {
        assertError(call("GET", "/api/incidents?status=FOO", "A6-ADMIN", null), 400, "INVALID_INPUT");
        assertError(call("GET", "/api/incidents?priority=P9", "A6-ADMIN", null), 400, "INVALID_INPUT");
        assertError(call("GET", "/api/incidents?slaStatus=NOPE", "A6-ADMIN", null), 400, "INVALID_INPUT");
        assertError(call("GET", "/api/incidents?createdFrom=yesterday", "A6-ADMIN", null), 400, "INVALID_INPUT");
        assertError(call("GET", "/api/incidents?createdFrom=2026-11-17T00:00:00Z&createdTo=2026-11-16T00:00:00Z",
                "A6-ADMIN", null), 400, "INVALID_INPUT");
    }

    // ---------- A5 behaviour preserved ----------

    @Test
    void authEndpoints_stillWork_andNowUseTheContractErrorFormat() throws Exception {
        HttpResponse<String> invalidLogin = call("POST", "/api/auth/login", null,
                "{\"id\":\"A6-NOBODY\",\"password\":\"whatever-123\"}");
        assertError(invalidLogin, 401, "INVALID_LOGIN");

        HttpResponse<String> invalidRegister = call("POST", "/api/auth/register", null,
                "{\"id\":\"A6-REG\",\"name\":\"Reg\",\"email\":\"reg@corp.com\",\"department\":\"IT\","
                        + "\"password\":\"short\"}");
        assertError(invalidRegister, 400, "INVALID_INPUT");

        HttpResponse<String> login = call("POST", "/api/auth/login", null,
                "{\"id\":\"A6-ADMIN\",\"password\":\"A6-Bootstrap-Pass-1\"}");
        assertEquals(200, login.statusCode(), login.body());
        assertNotNull(field(login.body(), "accessToken"));
    }
}
