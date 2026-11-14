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



