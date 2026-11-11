-- A3: I-ITSM schema (derived from CONTRACT_FREEZE.md §D, §E, §G.1, §M, §P.1 and AuditEntry)

CREATE TABLE categories (
    id     VARCHAR(100) PRIMARY KEY,
    name   VARCHAR(200) NOT NULL,
    active BOOLEAN      NOT NULL
);

CREATE TABLE it_services (
    id                   VARCHAR(100) PRIMARY KEY,
    name                 VARCHAR(200) NOT NULL,
    business_criticality VARCHAR(20)  NOT NULL,
    CONSTRAINT ck_it_services_criticality
        CHECK (business_criticality IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);

CREATE TABLE users (
    id                   VARCHAR(100) PRIMARY KEY,
    name                 VARCHAR(200) NOT NULL,
    email                VARCHAR(320) NOT NULL,
    user_role            VARCHAR(20)  NOT NULL,
    active               BOOLEAN      NOT NULL,
    department           VARCHAR(200),
    years_of_experience  INTEGER,
    max_active_incidents INTEGER,
    available            BOOLEAN,
    CONSTRAINT ck_users_role
        CHECK (user_role IN ('EMPLOYEE', 'TECHNICIAN', 'TEAM_LEAD', 'ADMINISTRATOR', 'IT_MANAGER')),
    CONSTRAINT ck_users_role_fields CHECK (
        (user_role = 'EMPLOYEE'
            AND department IS NOT NULL
            AND years_of_experience IS NULL AND max_active_incidents IS NULL AND available IS NULL)
        OR
        (user_role IN ('TECHNICIAN', 'TEAM_LEAD')
            AND department IS NULL
            AND years_of_experience >= 0 AND max_active_incidents >= 1 AND available IS NOT NULL)
        OR
        (user_role IN ('ADMINISTRATOR', 'IT_MANAGER')
            AND department IS NULL
            AND years_of_experience IS NULL AND max_active_incidents IS NULL AND available IS NULL)
    )
);

CREATE TABLE technician_skills (
    technician_id VARCHAR(100) NOT NULL,
    category_id   VARCHAR(100) NOT NULL,
    skill_level   INTEGER      NOT NULL,
    PRIMARY KEY (technician_id, category_id),
    CONSTRAINT fk_skills_technician FOREIGN KEY (technician_id) REFERENCES users (id),
    CONSTRAINT fk_skills_category   FOREIGN KEY (category_id)   REFERENCES categories (id),
    CONSTRAINT ck_skills_level CHECK (skill_level BETWEEN 1 AND 5)
);

-- policy_key follows CONTRACT_FREEZE.md §M: "<serviceId>|<priority>" or "*|<priority>" for defaults.
CREATE TABLE sla_policies (
    policy_key                VARCHAR(160) PRIMARY KEY,
    service_id                VARCHAR(100),
    priority                  VARCHAR(2)   NOT NULL,
    response_target_seconds   BIGINT       NOT NULL,
    resolution_target_seconds BIGINT       NOT NULL,
    CONSTRAINT fk_sla_service FOREIGN KEY (service_id) REFERENCES it_services (id),
    CONSTRAINT ck_sla_priority CHECK (priority IN ('P1', 'P2', 'P3', 'P4')),
    CONSTRAINT ck_sla_targets CHECK (
        response_target_seconds > 0
        AND resolution_target_seconds > 0
        AND response_target_seconds <= resolution_target_seconds),
    CONSTRAINT ck_sla_policy_key CHECK (policy_key = COALESCE(service_id, '*') || '|' || priority)
);

CREATE TABLE incidents (
    id                      VARCHAR(100)  PRIMARY KEY,
    title                   VARCHAR(200)  NOT NULL,
    description             VARCHAR(4000) NOT NULL,
    reporter_id             VARCHAR(100)  NOT NULL,
    service_id              VARCHAR(100)  NOT NULL,
    category_id             VARCHAR(100)  NOT NULL,
    suggested_category_id   VARCHAR(100),
    category_source         VARCHAR(10)   NOT NULL,
    impact                  VARCHAR(20)   NOT NULL,
    urgency                 VARCHAR(20)   NOT NULL,
    affected_users          INTEGER       NOT NULL,
    priority                VARCHAR(2)    NOT NULL,
    priority_score          INTEGER       NOT NULL,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    status                  VARCHAR(20)   NOT NULL,
    assigned_technician_id  VARCHAR(100),
    assigned_at             TIMESTAMP WITH TIME ZONE,
    acknowledged_at         TIMESTAMP WITH TIME ZONE,
    verified_at             TIMESTAMP WITH TIME ZONE,
    closed_at               TIMESTAMP WITH TIME ZONE,
    closed_by_id            VARCHAR(100),
    closure_note            VARCHAR(4000),
    response_deadline       TIMESTAMP WITH TIME ZONE,
    resolution_deadline     TIMESTAMP WITH TIME ZONE,
    waiting_since           TIMESTAMP WITH TIME ZONE,
    paused_duration_nanos   BIGINT        NOT NULL DEFAULT 0,
    resolution_resolver_id  VARCHAR(100),
    resolution_summary      VARCHAR(4000),
    resolution_resolved_at  TIMESTAMP WITH TIME ZONE,
    feedback_author_id      VARCHAR(100),
    feedback_rating         INTEGER,
    feedback_comment        VARCHAR(1000),
    feedback_submitted_at   TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_incidents_reporter  FOREIGN KEY (reporter_id)            REFERENCES users (id),
    CONSTRAINT fk_incidents_service   FOREIGN KEY (service_id)             REFERENCES it_services (id),
    CONSTRAINT fk_incidents_category  FOREIGN KEY (category_id)            REFERENCES categories (id),
    CONSTRAINT fk_incidents_suggested FOREIGN KEY (suggested_category_id)  REFERENCES categories (id),
    CONSTRAINT fk_incidents_assignee  FOREIGN KEY (assigned_technician_id) REFERENCES users (id),
    CONSTRAINT fk_incidents_closed_by FOREIGN KEY (closed_by_id)           REFERENCES users (id),
    CONSTRAINT fk_incidents_resolver  FOREIGN KEY (resolution_resolver_id) REFERENCES users (id),
    CONSTRAINT fk_incidents_fb_author FOREIGN KEY (feedback_author_id)     REFERENCES users (id),
    CONSTRAINT ck_incidents_source  CHECK (category_source IN ('MANUAL', 'KEYWORD', 'LLM')),
    CONSTRAINT ck_incidents_impact  CHECK (impact  IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_incidents_urgency CHECK (urgency IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_incidents_priority CHECK (priority IN ('P1', 'P2', 'P3', 'P4')),
    CONSTRAINT ck_incidents_status CHECK (status IN
        ('NEW', 'CLASSIFIED', 'ASSIGNED', 'ACKNOWLEDGED', 'IN_PROGRESS',
         'WAITING_FOR_USER', 'RESOLVED', 'VERIFIED', 'CLOSED')),
    CONSTRAINT ck_incidents_affected CHECK (affected_users >= 1),
    CONSTRAINT ck_incidents_score    CHECK (priority_score >= 1),
    CONSTRAINT ck_incidents_paused   CHECK (paused_duration_nanos >= 0),
    -- §G.1 / §O: KEYWORD or LLM => suggestion present and equal to the final category.
    -- MANUAL => no constraint on the suggestion (it may be absent, or present and different = override).
    CONSTRAINT ck_incidents_suggestion CHECK (
        category_source = 'MANUAL'
        OR (category_source IN ('KEYWORD', 'LLM')
            AND suggested_category_id IS NOT NULL
            AND suggested_category_id = category_id)),
    CONSTRAINT ck_incidents_deadlines CHECK (
        (response_deadline IS NULL AND resolution_deadline IS NULL)
        OR (response_deadline IS NOT NULL AND resolution_deadline IS NOT NULL
            AND created_at <= response_deadline
            AND response_deadline <= resolution_deadline)),
    CONSTRAINT ck_incidents_resolution CHECK (
        (resolution_resolver_id IS NULL AND resolution_summary IS NULL AND resolution_resolved_at IS NULL)
        OR (resolution_resolver_id IS NOT NULL AND resolution_summary IS NOT NULL
            AND resolution_resolved_at IS NOT NULL)),
    CONSTRAINT ck_incidents_feedback CHECK (
        (feedback_author_id IS NULL AND feedback_rating IS NULL
            AND feedback_comment IS NULL AND feedback_submitted_at IS NULL)
        OR (feedback_author_id IS NOT NULL AND feedback_rating BETWEEN 1 AND 5
            AND feedback_comment IS NOT NULL AND feedback_submitted_at IS NOT NULL))
);

CREATE TABLE incident_notes (
    id          BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    incident_id VARCHAR(100)  NOT NULL,
    author_id   VARCHAR(100)  NOT NULL,
    note_type   VARCHAR(20)   NOT NULL,
    note_text   VARCHAR(4000) NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_notes_incident FOREIGN KEY (incident_id) REFERENCES incidents (id),
    CONSTRAINT fk_notes_author   FOREIGN KEY (author_id)   REFERENCES users (id),
    CONSTRAINT ck_notes_type CHECK (note_type IN
        ('INVESTIGATION', 'INFO_REQUEST', 'INFO_RESPONSE', 'ADDITIONAL_INFO'))
);

-- actor_id has no FK on purpose: events can be raised by the SYSTEM actor.
CREATE TABLE audit_entries (
    id              BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    occurred_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    actor_id        VARCHAR(100)  NOT NULL,
    incident_id     VARCHAR(100)  NOT NULL,
    previous_status VARCHAR(20),
    new_status      VARCHAR(20)   NOT NULL,
    action          VARCHAR(100)  NOT NULL,
    description     VARCHAR(4000) NOT NULL
);