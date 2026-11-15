# AI Usage Log — Teammate (Developer B)

## Session 1: Gate G1 & G1E (2026-11-10)
- **Task:** Implemented Gate G1 (Incident Lifecycle State Pattern) and Gate G1E (Domain Events & SLA enums).
- **Files Created:**
  - `com.iitsm.domain.incident`: `IncidentState`, `AbstractIncidentState`, 9 concrete states (`NewState`, `ClassifiedState`, `AssignedState`, `AcknowledgedState`, `InProgressState`, `WaitingForUserState`, `ResolvedState`, `VerifiedState`, `ClosedState`), `IncidentStates`.
  - `com.iitsm.domain.sla`: `SlaType`, `SlaStatus`.
  - `com.iitsm.domain.event`: `DomainEvent`, `DomainEventListener`, `DomainEventPublisher`, and 12 immutable domain event records.
  - `com.iitsm.infrastructure.event`: `InMemoryDomainEventPublisher`.
  - Tests: `IncidentStateTransitionTest` (all 99 transitions matrix + guard checks), `InMemoryDomainEventPublisherTest`, `EventRecordsTest`.
- **Outcome:** Clean compile, 0 framework annotations, all lifecycle transitions verified.

## Session 2: Gate G3 (2026-11-11)
- **Task:** Implemented Gate G3 (SLA calculation & evaluation, Application Commands, IncidentOperation, IncidentPermissionPolicy, IncidentService, and unit tests).
- **Files Created:**
  - `com.iitsm.domain.sla`: `SlaDeadlines`, `SlaEvaluation`, `SlaPolicyResolver`, `SlaCalculator`, `SlaEvaluator`.
  - `com.iitsm.application.command`: `CreateIncidentCommand`, `AssignIncidentCommand`, `UpdatePriorityInputsCommand`, `IncidentTextCommand`, `CloseIncidentCommand`, `SubmitFeedbackCommand`.
  - `com.iitsm.application.service`: `IncidentOperation`, `IncidentPermissionPolicy`, `IncidentService`.
  - Tests: `SlaPolicyResolverTest`, `SlaCalculatorTest`, `SlaEvaluatorTest`, `IncidentPermissionPolicyTest`, `IncidentServiceCreateTest`, `IncidentServiceAssignmentTest`, `IncidentServiceWorkflowTest`, `IncidentServiceQueryAndPriorityTest`.
  - Test Helpers: `ServiceFixture`, `StubAssignmentStrategy`, `RecordingListener`.
  - Documentation: `docs/sla-and-lifecycle-design.md`.
- **Outcome:** Total test suite passed at 272 tests with 0 failures, 0 errors.

## Session 3: Gate B5 (2026-11-13)
- **Task:** Implemented Gate B5 (Audit & Notification Spring configuration and persistence integration).
- **Files Created/Updated:**
  - `com.iitsm.config`: `AuditNotificationConfig` (subscribes `AuditEventListener` strictly before `NotificationEventListener`).
  - Unit & Integration Tests: `AuditNotificationConfigTest`, `JpaNotificationRepositoryTest`.
- **Outcome:** Clean compile, listener subscription ordering verified, repositories wired into Spring context.

## Session 4: Gate B6 (2026-11-13)
- **Task:** Implemented Gate B6 (Reporting and Admin REST Controllers and DTOs).
- **Files Created/Updated:**
  - `com.iitsm.config`: `ReportingAdminConfig`.
  - `com.iitsm.api`: `AuditController`, `NotificationController`, `DashboardController`, `ItServiceAdminController`, `CategoryAdminController`, `SlaPolicyAdminController`.
  - `com.iitsm.api.dto`: 11 DTO records for audit, notification, dashboard metrics, IT services, categories, and SLA policies.
  - Unit Tests: `AuditControllerTest`, `NotificationControllerTest`, `DashboardControllerTest`, `CatalogAdminControllersTest` (using `TestingAuthenticationToken`).
- **Outcome:** All reporting and admin endpoints tested with 100% pass rate.

## Session 5: Gate B7 (2026-11-14)
- **Task:** Implemented Gate B7 (Audit trail, notification delivery, listener order, and transactional resilience integration tests).
- **Files Created/Updated:**
  - `com.iitsm.integration`:
    - `AuditTrailIntegrationTest`: Full 11-step incident lifecycle audit verification proving all 7 audit fields in chronological order.
    - `NotificationIntegrationTest`: End-to-end notification dispatch, unread/read queries, mark-read lifecycle, and recipient isolation.
    - `EventListenerOrderTest`: Proves `AuditEventListener` strictly executes before `NotificationEventListener`.
    - `TransactionEventBehaviorTest`: Proves critical listener failures roll back incident transactions and non-critical notification failures do not poison or roll back outer transactions (Rollback Trap D1).
  - Documentation: `docs/sla-and-lifecycle-design.md`, `docs/ai-log-B.md`.
- **Outcome:** Full test suite passed (685 tests, 0 failures, 0 errors).

## Session 6: Phase 2 Step 5 & 7 — Frontend API Integration & Notifications (2026-11-14)
- **Task:** Implemented Phase 2 Step 7 ("Connect frontend to real APIs"), Step 2 ("Login/Register screens + real auth"), and Step 5 ("Notification UI").
- **Files Created/Updated:**
  - `frontend/src/components/NotificationBell.jsx`: In-app notification bell with real-time unread badge, popover tray, direct incident navigation, and mark-as-read controls.
  - `frontend/src/pages/LoginPage.jsx`: Dedicated login and employee self-registration screen.
  - `frontend/src/context/AuthContext.jsx`: Integrated stateless JWT bearer token decoding, token storage, and session lifecycle.
  - `frontend/src/api/apiClient.js`: Switched default mode to live backend API (`VITE_USE_MOCKS=false`), added token store and automatic bearer authorization, network error handling, real reference data endpoints, and `notificationApi` client.
  - `frontend/src/App.jsx`: Route guarding for unauthenticated users and admin-only routes.
  - `frontend/src/components/Navbar.jsx`: Embedded NotificationBell, dynamic user badge, and sign-out controls.
  - `frontend/src/test/realApi.test.js`: 11 integration unit tests covering real API mode and notification endpoints.
  - Code cleanups across frontend components for zero-warning ESLint compliance.
- **Outcome:** Frontend lint passed (0 warnings), build passed, all 17 frontend unit tests passed. Backend suite remains at 685 passing tests.

## Session 7: Gate B1 — SLA Foundation (2026-11-15)
- **Task:** Implemented Gate B1 (SLA Foundation domain model, repository contract, and in-memory implementation).
- **Files Created:**
  - `com.iitsm.domain.sla`: `SlaAlertKind`, `SlaAlert`, `SlaAlertRepository`.
  - `com.iitsm.infrastructure.persistence.memory`: `InMemorySlaAlertRepository`.
  - Unit Tests: `SlaAlertTest`, `InMemorySlaAlertRepositoryTest`.
- **Outcome:** Idempotent alert creation, natural key calculation (`incidentId:slaType:kind`), existence queries, and filtering by kind verified. Total backend suite: 695 tests passing, 0 failures, 0 errors. Unblocks Developer A for Gate A3.

## Session 8: Gate B2 — Smart Technician Recommendation (2026-11-15)
- **Task:** Implemented Gate B2 (Intelligent recommendation service separate from auto-assignment, utilizing `AssignmentStrategy.rank()` to provide deterministic ranked technician recommendations for open incidents).
- **Files Created:**
  - `com.iitsm.application.service.TechnicianRecommendation`: Value object model for ranked recommendations with active workload, capacity, experience, and score.
  - `com.iitsm.application.service.TechnicianRecommendationService`: Application service filtering available/non-overloaded candidates and delegating to active `AssignmentStrategy.rank()`.
  - `com.iitsm.api.dto.TechnicianRecommendationResponse`: REST DTO for client display.
  - `com.iitsm.api.TechnicianRecommendationController`: REST controller exposing `GET /api/incidents/{id}/recommendations`.
  - `com.iitsm.config.TechnicianRecommendationConfig`: Spring configuration wiring the service bean into the application context.
  - Unit & Integration Tests:
    - `TechnicianRecommendationTest`: Validates recommendation creation and validation rules.
    - `TechnicianRecommendationServiceTest`: 11 tests covering skill-based ranking, load-based ranking, availability filtering, overload filtering, missing candidates, role permissions, inactive actors, missing incidents, and closed incident guards.
    - `TechnicianRecommendationControllerTest`: 3 tests verifying authenticated admin access, employee permission rejection, and 404 on missing incidents.
    - `TechnicianRecommendationConfigTest`: Verifies Spring bean wiring.
- **Outcome:** Total backend test suite: 712 tests passing with 0 failures and 0 errors. Frontend remains green (17/17 tests passing, build passing, 0 ESLint warnings). Auto-assignment logic (`performAutoAssign`) preserved without modification. Intelligent Feature #2 complete.

## Session 9: Gate B3 — SLA Monitor Service (2026-11-15)
- **Task:** Implemented Gate B3 (SLA Monitor service evaluating open incidents, detecting warnings at 20% threshold and violations at deadline, idempotent alert persistence, SYSTEM-authored domain events, and per-incident transaction isolation).
- **Files Created:**
  - `com.iitsm.application.service.IncidentSlaResult`: Result value object tracking warnings and violations per incident.
  - `com.iitsm.application.service.SlaMonitorResult`: Summary metrics record for monitor scan runs.
  - `com.iitsm.application.service.SlaIncidentProcessor`: Isolated incident-level SLA evaluator with `@Transactional(propagation = Propagation.REQUIRES_NEW)`.
  - `com.iitsm.application.service.SlaMonitorService`: Core monitor scanning open incidents, skipping CLOSED incidents, evaluating SLA state via `SlaEvaluator`, idempotently saving `SlaAlert` to `SlaAlertRepository`, publishing `SlaWarning` / `SlaViolated` domain events with actor `SYSTEM`, and providing query methods (`getViolations()`, `getAtRiskAlerts()`).
  - `com.iitsm.config.SlaMonitorConfig`: Spring configuration providing `@ConditionalOnMissingBean` fallback to `InMemorySlaAlertRepository` until Gate A3 JPA repository arrives.
  - Unit & Integration Tests:
    - `SlaMonitorServiceTest`: 8 comprehensive tests verifying warning generated once, violation generated once, second tick idempotency, paused resolution SLA calculation, closed incident skipping, per-incident error isolation, late-resolved violation detection, and query filters.
    - `SlaMonitorConfigTest`: Spring bean wiring test.
- **Outcome:** Total backend test suite: 721 tests passing (0 failures, 0 errors). Frontend test suite: 17/17 tests passing, build passing, 0 ESLint warnings. Idempotent SLA monitoring verified and ready for Gate B4 scheduler & REST API.

## Session 10: Gate B4 — SLA API & Scheduler (2026-11-15)
- **Task:** Implemented Gate B4 (Background scheduler with configurable interval and property condition, admin manual-run endpoint `POST /api/sla/monitor/run`, violation endpoint `GET /api/sla/violations`, and warning/at-risk endpoint `GET /api/sla/at-risk`).
- **Files Created:**
  - `com.iitsm.api.dto.SlaAlertResponse`: Response DTO for SLA alert presentation.
  - `com.iitsm.api.dto.SlaMonitorRunResponse`: Response DTO for manual monitor execution stats.
  - `com.iitsm.infrastructure.scheduler.SlaScheduler`: Background scheduled monitor component guarded by `@ConditionalOnProperty(name = "iitsm.sla.monitor.enabled", havingValue = "true")` with configurable fixed delay (`${iitsm.sla.monitor.interval-ms:30000}`).
  - `com.iitsm.config.SlaSchedulerConfig`: Configuration enabling `@EnableScheduling` conditionally when property is true, keeping test baseline unburdened by active schedulers.
  - `com.iitsm.api.SlaApiController`: REST controller exposing `POST /api/sla/monitor/run` (admin only), `GET /api/sla/violations`, and `GET /api/sla/at-risk` with optional incident filter.
  - Unit & Integration Tests:
    - `SlaApiControllerTest`: 5 tests verifying admin manual trigger, employee 403, technician 403, violation queries with incident filtering, and at-risk queries with incident filtering.
    - `SlaSchedulerTest`: 2 tests verifying scheduler invocation and exception isolation without rethrowing.
- **Outcome:** Total backend test suite: 728 tests passing (0 failures, 0 errors). Frontend remains green (17/17 tests passing, build passing, 0 ESLint warnings). B4 complete and unblocked.







