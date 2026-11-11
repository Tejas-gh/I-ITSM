-- A3 seed data: CONTRACT_FREEZE.md §D.6 (categories) and §L.1 (default SLA policies)

INSERT INTO categories (id, name, active) VALUES ('HARDWARE',       'Hardware',       TRUE);
INSERT INTO categories (id, name, active) VALUES ('SOFTWARE',       'Software',       TRUE);
INSERT INTO categories (id, name, active) VALUES ('NETWORK',        'Network',        TRUE);
INSERT INTO categories (id, name, active) VALUES ('DATABASE',       'Database',       TRUE);
INSERT INTO categories (id, name, active) VALUES ('CLOUD',          'Cloud',          TRUE);
INSERT INTO categories (id, name, active) VALUES ('SECURITY',       'Security',       TRUE);
INSERT INTO categories (id, name, active) VALUES ('AUTHENTICATION', 'Authentication', TRUE);
INSERT INTO categories (id, name, active) VALUES ('APPLICATION',    'Application',    TRUE);
INSERT INTO categories (id, name, active) VALUES ('STORAGE',        'Storage',        TRUE);

INSERT INTO sla_policies (policy_key, service_id, priority, response_target_seconds, resolution_target_seconds)
VALUES ('*|P1', NULL, 'P1', 300, 1800);
INSERT INTO sla_policies (policy_key, service_id, priority, response_target_seconds, resolution_target_seconds)
VALUES ('*|P2', NULL, 'P2', 900, 7200);
INSERT INTO sla_policies (policy_key, service_id, priority, response_target_seconds, resolution_target_seconds)
VALUES ('*|P3', NULL, 'P3', 3600, 28800);
INSERT INTO sla_policies (policy_key, service_id, priority, response_target_seconds, resolution_target_seconds)
VALUES ('*|P4', NULL, 'P4', 14400, 86400);