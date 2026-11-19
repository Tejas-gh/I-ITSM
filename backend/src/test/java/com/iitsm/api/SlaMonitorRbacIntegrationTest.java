package com.iitsm.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.iitsm.application.service.AuthService;
import com.iitsm.domain.factory.UserDraft;
import com.iitsm.domain.model.Role;

/** SLA monitoring RBAC over real HTTP: employees get 403, technicians and administrators are allowed. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:sla-rbac;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class SlaMonitorRbacIntegrationTest {

    private static final String PASSWORD = "Staff-Pass-12345";
    private static final Pattern TOKEN = Pattern.compile("\"accessToken\"\\s*:\\s*\"([^\"]+)\"");

    private final HttpClient http = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private AuthService authService;

    private static boolean created;

    @BeforeAll
    static void reset() {
        created = false;
    }

    private void ensureUsers() {
        if (created) {
            return;
        }
        authService.createAccount(new UserDraft("RBAC-EMP", "Emp", "rbac-emp@corp.com", Role.EMPLOYEE,
                "IT", null, 0, 0), PASSWORD);
        authService.createAccount(new UserDraft("RBAC-TECH", "Tech", "rbac-tech@corp.com", Role.TECHNICIAN,
                null, Map.of(), 1, 5), PASSWORD);
        authService.createAccount(new UserDraft("RBAC-ADMIN", "Admin", "rbac-admin@corp.com", Role.ADMINISTRATOR,
                null, null, 0, 0), PASSWORD);
        created = true;
    }

    private String token(String id) throws Exception {
        ensureUsers();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString("{\"id\":\"" + id + "\",\"password\":\"" + PASSWORD + "\"}"))
                .build();
        HttpResponse<String> response = http.send(request, BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        Matcher m = TOKEN.matcher(response.body());
        assertTrue(m.find(), "login response must contain a token");
        return m.group(1);
    }

    private int status(String method, String path, String bearer) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (bearer != null) {
            b.header("Authorization", "Bearer " + bearer);
        }
        b.method(method, BodyPublishers.noBody());
        return http.send(b.build(), BodyHandlers.ofString()).statusCode();
    }

    @Test
    void slaReadEndpoints_employeeIsForbidden() throws Exception {
        String employee = token("RBAC-EMP");

        assertEquals(403, status("GET", "/api/sla/violations", employee));
        assertEquals(403, status("GET", "/api/sla/at-risk", employee));
    }

    @Test
    void slaReadEndpoints_technicianIsAllowed() throws Exception {
        String technician = token("RBAC-TECH");

        assertEquals(200, status("GET", "/api/sla/violations", technician));
        assertEquals(200, status("GET", "/api/sla/at-risk", technician));
    }

    @Test
    void slaReadEndpoints_administratorIsAllowed() throws Exception {
        String admin = token("RBAC-ADMIN");

        assertEquals(200, status("GET", "/api/sla/violations", admin));
        assertEquals(200, status("GET", "/api/sla/at-risk", admin));
    }

    @Test
    void slaReadEndpoints_withoutTokenIsUnauthorized() throws Exception {
        assertEquals(401, status("GET", "/api/sla/violations", null));
        assertEquals(401, status("GET", "/api/sla/at-risk", null));
    }

    @Test
    void manualMonitorRun_remainsAdministratorOnly() throws Exception {
        assertEquals(403, status("POST", "/api/sla/monitor/run", token("RBAC-EMP")));
        assertEquals(403, status("POST", "/api/sla/monitor/run", token("RBAC-TECH")));
        assertEquals(200, status("POST", "/api/sla/monitor/run", token("RBAC-ADMIN")));
    }
}
