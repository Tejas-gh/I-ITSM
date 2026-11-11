package com.iitsm.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class FlywayMigrationTest {

    private static final String SERVICE_INSERT =
            "INSERT INTO it_services (id, name, business_criticality) VALUES ('SVC1', 'Payment', 'HIGH')";

    private static final String EMPLOYEE_INSERT =
            "INSERT INTO users (id, name, email, user_role, active, department) "
            + "VALUES ('E1', 'Eve', 'e@x.com', 'EMPLOYEE', TRUE, 'Finance')";

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Flyway flyway;

    // ---------- migration / schema / seed ----------

    @Test
    void migrations_testProfile_allAppliedWithNonePending() {
        assertEquals(0, flyway.info().pending().length);
        assertTrue(flyway.info().applied().length >= 2);
    }

    @Test
    void schema_afterMigration_containsAllTables() throws SQLException {
        List<String> tables = queryStrings(
                "SELECT UPPER(table_name) FROM information_schema.tables "
                + "WHERE UPPER(table_schema) = 'PUBLIC' AND UPPER(table_name) IN "
                + "('USERS','TECHNICIAN_SKILLS','IT_SERVICES','CATEGORIES','SLA_POLICIES',"
                + "'INCIDENTS','INCIDENT_NOTES','AUDIT_ENTRIES') ORDER BY 1");
        assertEquals(List.of("AUDIT_ENTRIES", "CATEGORIES", "INCIDENTS", "INCIDENT_NOTES",
                "IT_SERVICES", "SLA_POLICIES", "TECHNICIAN_SKILLS", "USERS"), tables);
    }

    @Test
    void seed_categories_containsNineSpecCategoriesAllActive() throws SQLException {
        assertEquals(List.of("APPLICATION", "AUTHENTICATION", "CLOUD", "DATABASE", "HARDWARE",
                "NETWORK", "SECURITY", "SOFTWARE", "STORAGE"),
                queryStrings("SELECT id FROM categories ORDER BY id"));
        assertEquals(List.of("9"),
                queryStrings("SELECT COUNT(*) FROM categories WHERE active = TRUE"));
    }

    @Test
    void seed_slaPolicies_matchSpecDefaultTargets() throws SQLException {
        assertEquals(List.of("P1|300|1800", "P2|900|7200", "P3|3600|28800", "P4|14400|86400"),
                queryStrings("SELECT priority || '|' || response_target_seconds || '|' "
                        + "|| resolution_target_seconds FROM sla_policies "
                        + "WHERE service_id IS NULL ORDER BY priority"));
    }

    @Test
    void seed_noUsersOrServices() throws SQLException {
        assertEquals(List.of("0"), queryStrings("SELECT COUNT(*) FROM users"));
        assertEquals(List.of("0"), queryStrings("SELECT COUNT(*) FROM it_services"));
    }

    // ---------- users / skills ----------

    @Test
    void constraint_employeeWithoutDepartment_rejected() throws SQLException {
        assertRejected("INSERT INTO users (id, name, email, user_role, active) "
                + "VALUES ('E1', 'Eve', 'e@x.com', 'EMPLOYEE', TRUE)");
    }

    @Test
    void constraint_technicianWithZeroMaxActive_rejected() throws SQLException {
        assertRejected("INSERT INTO users (id, name, email, user_role, active, "
                + "years_of_experience, max_active_incidents, available) "
                + "VALUES ('T1', 'Tom', 't@x.com', 'TECHNICIAN', TRUE, 5, 0, TRUE)");
    }

    @Test
    void constraint_skillLevelOutOfRange_rejected() throws SQLException {
        assertRejected(
                "INSERT INTO users (id, name, email, user_role, active, "
                + "years_of_experience, max_active_incidents, available) "
                + "VALUES ('T1', 'Tom', 't@x.com', 'TECHNICIAN', TRUE, 5, 3, TRUE)",
                "INSERT INTO technician_skills (technician_id, category_id, skill_level) "
                + "VALUES ('T1', 'NETWORK', 6)");
    }

    // ---------- SLA policies ----------

    @Test
    void constraint_duplicateDefaultSlaPolicy_rejected() throws SQLException {
        assertRejected("INSERT INTO sla_policies (policy_key, service_id, priority, "
                + "response_target_seconds, resolution_target_seconds) VALUES ('*|P1', NULL, 'P1', 60, 120)");
    }

    @Test
    void constraint_slaPolicyKeyMismatch_rejected() throws SQLException {
        assertRejected(SERVICE_INSERT,
                "INSERT INTO sla_policies (policy_key, service_id, priority, "
                + "response_target_seconds, resolution_target_seconds) VALUES ('wrong', 'SVC1', 'P1', 60, 120)");
    }

    // ---------- incidents ----------

    @Test
    void constraint_incidentWithUnknownReporter_rejected() throws SQLException {
        assertRejected(SERVICE_INSERT,
                incident("MISSING", "'MANUAL'", "NULL", "NETWORK", "'NEW'", ""));
    }

    @Test
    void constraint_incidentInvalidStatus_rejected() throws SQLException {
        assertRejected(SERVICE_INSERT, EMPLOYEE_INSERT,
                incident("E1", "'MANUAL'", "NULL", "NETWORK", "'CANCELLED'", ""));
    }

    @Test
    void constraint_keywordSourceWithNullSuggestion_rejected() throws SQLException {
        assertRejected(SERVICE_INSERT, EMPLOYEE_INSERT,
                incident("E1", "'KEYWORD'", "NULL", "NETWORK", "'NEW'", ""));
    }

    @Test
    void constraint_llmSourceWithDifferentSuggestion_rejected() throws SQLException {
        assertRejected(SERVICE_INSERT, EMPLOYEE_INSERT,
                incident("E1", "'LLM'", "'CLOUD'", "NETWORK", "'NEW'", ""));
    }

    @Test
    void constraint_keywordSourceWithMatchingSuggestion_accepted() throws SQLException {
        assertAccepted(SERVICE_INSERT, EMPLOYEE_INSERT,
                incident("E1", "'KEYWORD'", "'NETWORK'", "NETWORK", "'NEW'", ""));
    }

    @Test
    void constraint_manualSourceWithoutOrWithDifferentSuggestion_accepted() throws SQLException {
        // CONTRACT_FREEZE.md §O: MANUAL may have no suggestion, or a different one (override).
        assertAccepted(SERVICE_INSERT, EMPLOYEE_INSERT,
                incident("E1", "'MANUAL'", "NULL", "NETWORK", "'NEW'", ""));
        assertAccepted(SERVICE_INSERT, EMPLOYEE_INSERT,
                incident("E1", "'MANUAL'", "'CLOUD'", "NETWORK", "'NEW'", ""));
    }

    @Test
    void constraint_feedbackOnlyRating_rejected() throws SQLException {
        assertRejected(SERVICE_INSERT, EMPLOYEE_INSERT,
                incident("E1", "'MANUAL'", "NULL", "NETWORK", "'CLOSED'", ", feedback_rating", ", 5"));
    }

    @Test
    void constraint_feedbackMissingComment_rejected() throws SQLException {
        assertRejected(SERVICE_INSERT, EMPLOYEE_INSERT,
                incident("E1", "'MANUAL'", "NULL", "NETWORK", "'CLOSED'",
                        ", feedback_author_id, feedback_rating, feedback_submitted_at",
                        ", 'E1', 5, CURRENT_TIMESTAMP"));
    }

    @Test
    void constraint_feedbackRatingOutOfRange_rejected() throws SQLException {
        assertRejected(SERVICE_INSERT, EMPLOYEE_INSERT,
                incident("E1", "'MANUAL'", "NULL", "NETWORK", "'CLOSED'", FEEDBACK_COLUMNS,
                        ", 'E1', 6, 'ok', CURRENT_TIMESTAMP"));
    }

    @Test
    void constraint_feedbackComplete_accepted() throws SQLException {
        assertAccepted(SERVICE_INSERT, EMPLOYEE_INSERT,
                incident("E1", "'MANUAL'", "NULL", "NETWORK", "'CLOSED'", FEEDBACK_COLUMNS,
                        ", 'E1', 5, '', CURRENT_TIMESTAMP"));
    }

    // ---------- audit ----------

    @Test
    void constraint_auditNullNewStatus_rejected() throws SQLException {
        assertRejected("INSERT INTO audit_entries (occurred_at, actor_id, incident_id, previous_status, "
                + "new_status, action, description) "
                + "VALUES (CURRENT_TIMESTAMP, 'SYSTEM', 'INC1001', 'NEW', NULL, 'X', 'd')");
    }

    @Test
    void constraint_auditNullPreviousStatus_accepted() throws SQLException {
        assertAccepted("INSERT INTO audit_entries (occurred_at, actor_id, incident_id, previous_status, "
                + "new_status, action, description) "
                + "VALUES (CURRENT_TIMESTAMP, 'SYSTEM', 'INC1001', NULL, 'NEW', 'INCIDENT_CREATED', 'd')");
    }

    // ---------- helpers ----------

    private static final String FEEDBACK_COLUMNS =
            ", feedback_author_id, feedback_rating, feedback_comment, feedback_submitted_at";

    /** Builds an incident INSERT; extraColumns/extraValues (each starting with ", ") add optional columns. */
    private static String incident(String reporterId, String source, String suggested, String category,
                                   String status, String extraColumns, String... extraValues) {
        String values = extraValues.length > 0 ? extraValues[0] : "";
        return "INSERT INTO incidents (id, title, description, reporter_id, service_id, category_id, "
                + "suggested_category_id, category_source, impact, urgency, affected_users, priority, "
                + "priority_score, created_at, status" + extraColumns + ") "
                + "VALUES ('INC1001', 't', 'd', '" + reporterId + "', 'SVC1', '" + category + "', "
                + suggested + ", " + source + ", 'HIGH', 'HIGH', 1, 'P2', 18, CURRENT_TIMESTAMP, "
                + status + values + ")";
    }

    private List<String> queryStrings(String sql) throws SQLException {
        List<String> result = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                result.add(rs.getString(1));
            }
        }
        return result;
    }

    /** Runs the statements in one transaction; the last one must fail. Always rolls back. */
    private void assertRejected(String... statements) throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try (Statement s = c.createStatement()) {
                for (int i = 0; i < statements.length - 1; i++) {
                    s.executeUpdate(statements[i]);
                }
                String last = statements[statements.length - 1];
                assertThrows(SQLException.class, () -> s.executeUpdate(last));
            } finally {
                c.rollback();
            }
        }
    }

    /** Runs all statements in one transaction; all must succeed. Always rolls back. */
    private void assertAccepted(String... statements) throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try (Statement s = c.createStatement()) {
                for (String sql : statements) {
                    s.executeUpdate(sql);
                }
            } finally {
                c.rollback();
            }
        }
    }
}