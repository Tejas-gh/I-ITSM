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

/**
 * Escalation endpoints through the real security chain over HTTP: the manual escalation run is administrator-only
 * (employee and technician get 403) and every escalation endpoint requires a valid token (no token gets 401).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:escalation-rbac;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class EscalationHttpRbacIntegrationTest {

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
        authService.createAccount(new UserDraft("ESC-EMP", "Emp", "esc-emp@corp.com", Role.EMPLOYEE,
                "IT", null, 0, 0), PASSWORD);
        authService.createAccount(new UserDraft("ESC-TECH", "Tech", "esc-tech@corp.com", Role.TECHNICIAN,
                null, Map.of(), 1, 5), PASSWORD);
        authService.createAccount(new UserDraft("ESC-ADMIN", "Admin", "esc-admin@corp.com", Role.ADMINISTRATOR,
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
    void manualEscalationRun_isAdministratorOnly() throws Exception {
        assertEquals(403, status("POST", "/api/escalations/run", token("ESC-EMP")));
        assertEquals(403, status("POST", "/api/escalations/run", token("ESC-TECH")));
        assertEquals(200, status("POST", "/api/escalations/run", token("ESC-ADMIN")));
    }

    @Test
    void escalationEndpoints_withoutToken_areUnauthorized() throws Exception {
        assertEquals(401, status("POST", "/api/escalations/run", null));
        assertEquals(401, status("GET", "/api/escalations", null));
        assertEquals(401, status("GET", "/api/incidents/INC-ANY/escalations", null));
    }
}
