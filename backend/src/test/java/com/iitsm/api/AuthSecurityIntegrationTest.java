package com.iitsm.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;

import com.iitsm.application.service.AuthService;
import com.iitsm.domain.model.Administrator;
import com.iitsm.domain.model.Employee;
import com.iitsm.domain.model.Role;
import com.iitsm.domain.model.User;
import com.iitsm.domain.repository.UserRepository;
import com.iitsm.infrastructure.security.BootstrapAdminInitializer;
import com.iitsm.infrastructure.security.JwtTokenService;

/**
 * A5 end-to-end security tests over real HTTP on a random port. They run against their own in-memory
 * database so the users they create never leak into the shared test database. The bootstrap credentials
 * are supplied explicitly here (and only here).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:a5-security;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "iitsm.bootstrap.admin.id=BOOT-ADMIN",
                "iitsm.bootstrap.admin.name=Bootstrap Admin",
                "iitsm.bootstrap.admin.email=boot-admin@corp.com",
                "iitsm.bootstrap.admin.password=Bootstrap-Test-Pass-1"
        })
@ActiveProfiles("test")
class AuthSecurityIntegrationTest {

    private static final String BOOT_ADMIN_ID = "BOOT-ADMIN";
    private static final String BOOT_ADMIN_PASSWORD = "Bootstrap-Test-Pass-1";
    private static final String USER_PASSWORD = "Employee-Pass-123";
    private static final String PROTECTED_PATH = "/api/incidents";
    private static final Pattern TOKEN = Pattern.compile("\"accessToken\"\\s*:\\s*\"([^\"]+)\"");
    private static final AtomicInteger COUNTER = new AtomicInteger();

    private final HttpClient http = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private UserRepository users;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private AuthService authService;

    @Autowired
    private BootstrapAdminInitializer bootstrapAdminInitializer;

    @Autowired
    private SecurityFilterChain securityFilterChain;

    // ---------- helpers ----------

    private HttpResponse<String> post(String path, String json) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(json))
                .build();
        return http.send(request, BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path, String authorizationHeader) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
        if (authorizationHeader != null) {
            builder.header("Authorization", authorizationHeader);
        }
        return http.send(builder.build(), BodyHandlers.ofString());
    }

    private static String newUserId() {
        return "E-A5-" + COUNTER.incrementAndGet();
    }

    private static String registerJson(String id, String password, String extraFields) {
        return "{\"id\":\"" + id + "\",\"name\":\"Test User\",\"email\":\"" + id.toLowerCase()
                + "@corp.com\",\"department\":\"IT\",\"password\":\"" + password + "\"" + extraFields + "}";
    }

    private static String loginJson(String id, String password) {
        return "{\"id\":\"" + id + "\",\"password\":\"" + password + "\"}";
    }

    private String registerEmployee() throws Exception {
        String id = newUserId();
        HttpResponse<String> response = post("/api/auth/register", registerJson(id, USER_PASSWORD, ""));
        assertEquals(201, response.statusCode());
        return id;
    }

    private String loginToken(String id, String password) throws Exception {
        HttpResponse<String> response = post("/api/auth/login", loginJson(id, password));
        assertEquals(200, response.statusCode());
        Matcher matcher = TOKEN.matcher(response.body());
        assertTrue(matcher.find(), "login response has no accessToken");
        return matcher.group(1);
    }

    private String queryString(String sql, String argument) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (argument != null) {
                statement.setString(1, argument);
            }
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    // ---------- context / wiring ----------

    @Test
    void context_securityAndJwtInfrastructure_isWired() {
        assertNotNull(securityFilterChain);
        assertNotNull(jwtDecoder);
        assertNotNull(jwtEncoder);
        assertNotNull(authService);
        assertNotNull(bootstrapAdminInitializer);
    }

    // ---------- bootstrap admin ----------

    @Test
    void bootstrapAdmin_createdAtStartup_asAdministratorWithBcryptHash() throws Exception {
        User admin = users.findById(BOOT_ADMIN_ID).orElseThrow();

        assertInstanceOf(Administrator.class, admin);
        assertEquals(Role.ADMINISTRATOR, admin.getRole());
        assertEquals("Bootstrap Admin", admin.getName());
        assertEquals("boot-admin@corp.com", admin.getEmail());

        String hash = queryString("SELECT password_hash FROM user_credentials WHERE user_id = ?", BOOT_ADMIN_ID);
        assertNotNull(hash);
        assertTrue(hash.startsWith("$2"), "stored credential is not a BCrypt hash");
        assertNotEquals(BOOT_ADMIN_PASSWORD, hash);
    }

    @Test
    void bootstrapAdmin_runAgain_doesNotDuplicateOrChangeTheAdministrator() throws Exception {
        String hashBefore = queryString("SELECT password_hash FROM user_credentials WHERE user_id = ?", BOOT_ADMIN_ID);

        bootstrapAdminInitializer.bootstrap();
        bootstrapAdminInitializer.bootstrap();

        assertEquals("1", queryString("SELECT COUNT(*) FROM users WHERE user_role = 'ADMINISTRATOR'", null));
        assertEquals(hashBefore, queryString("SELECT password_hash FROM user_credentials WHERE user_id = ?", BOOT_ADMIN_ID));
    }

    @Test
    void bootstrapAdmin_canLogInOverHttp_andTokenCarriesTheAdministratorRole() throws Exception {
        String token = loginToken(BOOT_ADMIN_ID, BOOT_ADMIN_PASSWORD);

        Jwt jwt = jwtDecoder.decode(token);

        assertEquals(BOOT_ADMIN_ID, jwt.getSubject());
        assertEquals("ADMINISTRATOR", jwt.getClaimAsString(JwtTokenService.ROLE_CLAIM));
    }

    // ---------- registration ----------

    @Test
    void register_validBody_returns201WithoutAnyPasswordInTheResponse() throws Exception {
        String id = newUserId();

        HttpResponse<String> response = post("/api/auth/register", registerJson(id, USER_PASSWORD, ""));

        assertEquals(201, response.statusCode());
        assertTrue(response.body().contains(id));
        assertTrue(response.body().contains("EMPLOYEE"));
        assertFalse(response.body().contains(USER_PASSWORD));
        assertFalse(response.body().toLowerCase().contains("password"));
    }

    @Test
    void register_thenLogin_returnsABearerTokenForAnEmployee() throws Exception {
        String id = registerEmployee();

        HttpResponse<String> response = post("/api/auth/login", loginJson(id, USER_PASSWORD));

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"tokenType\":\"Bearer\""));
        Matcher matcher = TOKEN.matcher(response.body());
        assertTrue(matcher.find());
        Jwt jwt = jwtDecoder.decode(matcher.group(1));
        assertEquals(id, jwt.getSubject());
        assertEquals("EMPLOYEE", jwt.getClaimAsString(JwtTokenService.ROLE_CLAIM));
    }

    @Test
    void register_bodyTryingToChooseARole_stillCreatesAnEmployeeOnly() throws Exception {
        String id = newUserId();

        HttpResponse<String> response =
                post("/api/auth/register", registerJson(id, USER_PASSWORD, ",\"role\":\"ADMINISTRATOR\""));

        assertEquals(201, response.statusCode());
        User created = users.findById(id).orElseThrow();
        assertInstanceOf(Employee.class, created);
        assertEquals(Role.EMPLOYEE, created.getRole());
        assertEquals("1", queryString("SELECT COUNT(*) FROM users WHERE user_role = 'ADMINISTRATOR'", null));
    }

    @Test
    void register_duplicateId_returns400AndKeepsTheOriginalPassword() throws Exception {
        String id = registerEmployee();

        HttpResponse<String> response = post("/api/auth/register", registerJson(id, "Another-Pass-999", ""));

        assertEquals(400, response.statusCode());
        loginToken(id, USER_PASSWORD);
    }

    @Test
    void register_weakPassword_returns400AndCreatesNoUser() throws Exception {
        String id = newUserId();

        HttpResponse<String> response = post("/api/auth/register", registerJson(id, "short", ""));

        assertEquals(400, response.statusCode());
        assertTrue(users.findById(id).isEmpty());
    }

    @Test
    void register_persistsOnlyABcryptHash() throws Exception {
        String id = registerEmployee();

        String hash = queryString("SELECT password_hash FROM user_credentials WHERE user_id = ?", id);

        assertNotNull(hash);
        assertTrue(hash.startsWith("$2"));
        assertNotEquals(USER_PASSWORD, hash);
        assertFalse(hash.contains(USER_PASSWORD));
    }

    // ---------- login failures ----------

    @Test
    void login_wrongPassword_returns401() throws Exception {
        String id = registerEmployee();

        assertEquals(401, post("/api/auth/login", loginJson(id, "Wrong-Password-1")).statusCode());
    }

    @Test
    void login_unknownUser_returns401() throws Exception {
        assertEquals(401, post("/api/auth/login", loginJson("NO-SUCH-USER", USER_PASSWORD)).statusCode());
    }

    @Test
    void login_inactiveUser_returns401() throws Exception {
        String id = registerEmployee();
        User user = users.findById(id).orElseThrow();
        user.deactivate();
        users.save(user);

        assertEquals(401, post("/api/auth/login", loginJson(id, USER_PASSWORD)).statusCode());
    }

    @Test
    void login_missingFields_returns400() throws Exception {
        assertEquals(400, post("/api/auth/login", "{}").statusCode());
    }

    // ---------- protected / public behaviour ----------

    @Test
    void protectedEndpoint_withoutToken_returns401() throws Exception {
        assertEquals(401, get(PROTECTED_PATH, null).statusCode());
    }

    @Test
    void protectedEndpoint_withValidToken_isNotRejectedBySecurity() throws Exception {
        String token = loginToken(registerEmployee(), USER_PASSWORD);

        int status = get(PROTECTED_PATH, "Bearer " + token).statusCode();

        assertNotEquals(401, status);
        assertNotEquals(403, status);
    }

    @Test
    void protectedEndpoint_withTamperedToken_returns401() throws Exception {
        String token = loginToken(registerEmployee(), USER_PASSWORD);
        String[] parts = token.split("\\.");
        String forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"BOOT-ADMIN\",\"role\":\"ADMINISTRATOR\",\"exp\":4102444800}".getBytes());

        int status = get(PROTECTED_PATH, "Bearer " + parts[0] + "." + forgedPayload + "." + parts[2]).statusCode();

        assertEquals(401, status);
    }

    @Test
    void protectedEndpoint_withExpiredToken_returns401() throws Exception {
        Clock longAgo = Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC);
        Employee someone = new Employee("E-OLD", "Old Token", "old@corp.com", "IT");
        String expired = new JwtTokenService(jwtEncoder, Duration.ofHours(1), longAgo).issue(someone).value();

        assertEquals(401, get(PROTECTED_PATH, "Bearer " + expired).statusCode());
    }

    @Test
    void protectedEndpoint_withGarbageToken_returns401() throws Exception {
        assertEquals(401, get(PROTECTED_PATH, "Bearer not-a-real-token").statusCode());
    }
}
