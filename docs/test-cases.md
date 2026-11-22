# I-ITSM — Documented Test Cases

Official requirement: *at least 15 meaningful test cases covering normal operations, boundary conditions, invalid inputs, exceptions, authorization and business rules.*
This document lists 21 cases (TC-01 … TC-21). Every row names a real test class and method in `backend/src/test/java/com/iitsm/`. Cases marked **NEW** were added in the final hardening pass; all others already existed in the suite.

Run all backend tests: `cd backend && .\mvnw.cmd clean test`

## Coverage by requirement category

| Category (spec) | Cases |
|---|---|
| Normal operations | TC-01, TC-12, TC-13, TC-20 |
| Boundary conditions | TC-03, TC-05 |
| Invalid inputs | TC-02, TC-19, TC-21 |
| Exceptions | TC-02, TC-06, TC-21 |
| Authorization | TC-10, TC-11, TC-19 |
| Business rules | TC-04, TC-07, TC-08, TC-09, TC-15, TC-16, TC-17 |

## Test cases

| ID | Scenario | Test class | Test method | Level | Expected result |
|---|---|---|---|---|---|
| TC-01 | Normal incident lifecycle NEW → CLASSIFIED → ASSIGNED → ACKNOWLEDGED → IN_PROGRESS → RESOLVED → VERIFIED → CLOSED | `application/service/IncidentLifecycleInMemoryTest` | `completeLifecycle_happyPath_transitionsThroughAllStatesPublishesAllEventsAndMeetsSla` | Service + events | Every status reached in order, all events published, SLA ends MET |
| TC-01b | Same lifecycle over HTTP with feedback | `api/IncidentApiIntegrationTest` | `lifecycle_createToClosedWithFeedback_followsTheStateMachineOverHttp` | HTTP integration | Each endpoint returns the expected next status |
| TC-02 | Invalid lifecycle transition (all 9 states × 11 actions; e.g. NEW → close) | `domain/incident/IncidentStateTransitionTest` | `transition_allNinetyNineStateActionPairs_adhereToLifecycleMatrix` | Unit | 12 valid pairs succeed; 87 invalid throw `InvalidStateTransitionException` |
| TC-02b | Illegal transition over HTTP | `api/IncidentApiIntegrationTest` | `illegalTransitions_return409InvalidStateTransition` | HTTP integration | 409 `INVALID_STATE_TRANSITION` |
| TC-03 | Priority calculation: oracle table, inclusive thresholds, affected-user bump bands | `domain/priority/WeightedPriorityStrategyTest` | `calculate_oracleTable_returnsExpectedScoreAndLevel`, `calculate_thresholdBoundaries_areInclusive`, `calculate_affectedUsersBands_addExpectedBump` | Unit | Exact score and P1–P4 level per documented formula |
| TC-04 | Assignment strategies rank deterministically and exclude ineligible technicians | `domain/assignment/SkillBasedAssignmentStrategyTest` · `domain/assignment/LoadBasedAssignmentStrategyTest` | `rank_multipleEligible_higherSkillRanksFirstWithDescendingScores` · `rank_lessLoadedBeatsMoreSkilled`; both: `rank_completeTie_orderedByTechnicianIdDeterministically`, `rank_overloadedTechnician_isOmittedAtTheLimitButKeptJustBelow` | Unit | Best candidate first, ties broken by technician id, overloaded/unavailable omitted |
| TC-05 | SLA warning at 20 % remaining, no breach exactly at the deadline, breach 1 ms after | `domain/sla/SlaEvaluatorTest` | `evaluate_exactlyAtWarningThreshold_returnsWarning`, `evaluate_exactlyAtDeadline_notBreached`, `evaluate_oneMillisecondAfterDeadline_returnsBreached` | Unit (boundary) | WARNING / not BREACHED / BREACHED respectively |
| TC-06 | SLA monitor records each warning and violation once; skips closed and paused; isolates a failing incident; records late resolution | `application/service/SlaMonitorServiceTest` | `warningGeneratedOnceWhenEnteringWarningThreshold`, `violationGeneratedOnceWhenPastDeadline`, `secondTickDoesNotDuplicateAlertsOrEvents`, `closedIncidentSkipped`, `pausedIncidentDoesNotIncorrectlyBreach`, `perIncidentFailureIsolated`, `lateViolationRecordedForResolvedIncident` | Service | One alert per (incident, SLA type, kind); other incidents unaffected by a failure |
| TC-07 | **NEW** Automatic P1 escalation L1 → L2 → L3 driven by monitor ticks, with records, audit entries and recipients | `integration/EscalationChainEndToEndTest` | `monitorTicks_escalateP1ThroughAllLevels_withAuditAndRecipients` | Integration (in-memory stack) | TEAM_LEAD at deadline, ADMINISTRATOR at +1 target, IT_MANAGER at +2 targets; 3 SYSTEM audit entries; team lead notified at L1 only |
| TC-07b | Escalation levels at unit level | `application/service/EscalationServiceTest` | `p1Unacknowledged_escalatesToLevel1`, `subsequentEvaluation_escalatesToLevel2`, `subsequentEvaluation_escalatesToLevel3`, `nonP1_ignored` | Service | Only unacknowledged P1 escalates, level by level |
| TC-08 | **NEW** Acknowledgement stops escalation (after L1, and before the deadline) | `integration/EscalationChainEndToEndTest` | `acknowledgedAfterLevel1_stopsFurtherEscalation`, `acknowledgedBeforeDeadline_isNeverEscalatedByMonitor` | Integration | No L2/L3 after acknowledgement; none at all if acknowledged in time (no record, audit entry or notification) |
| TC-09 | **NEW** Escalation is idempotent across repeated ticks | `integration/EscalationChainEndToEndTest` | `repeatedTicks_doNotDuplicateEscalationOutputs` | Integration | One record, one audit entry, three notifications (admin, IT manager, team lead) |
| TC-10 | **NEW** Escalation-scoped RBAC: manual escalation run is admin-only over real HTTP security; recipients limited to target role and administrators | `api/EscalationHttpRbacIntegrationTest` · `application/notification/EscalationRecipientRulesTest` | `manualEscalationRun_isAdministratorOnly` · `level2_notifiesAdministratorsOnly`, `level1_inactiveTeamLeadIsNotNotified` | HTTP integration / Unit | Employee and technician get 403, administrator 200; L2 reaches administrators only; inactive team lead not notified |
| TC-10b | Escalation visibility/permissions scoped to the latest escalation level | `application/service/IncidentPermissionPolicyTest` · `application/service/IncidentSearchServiceTest` | `teamLead_escalationGrantsScopedViewAndTechnicianWorkflow`, `escalationAuthority_isScopedToLatestIncidentEscalation` · `teamLead_seesUnassignedIncidentEscalatedToTeamLead`, `teamLead_losesScopedVisibilityWhenL2BecomesLatest` | Service | Team lead sees and acts on an escalated incident only while L1 is the latest level |
| TC-11 | Unauthorized access: no token, wrong role | `api/IncidentApiIntegrationTest` · `api/SlaMonitorRbacIntegrationTest` · `api/EscalationHttpRbacIntegrationTest` (**NEW**) | `incidents_withoutToken_returns401`, `create_asTechnician_returns403InContractFormat` · `manualMonitorRun_remainsAdministratorOnly` · `escalationEndpoints_withoutToken_areUnauthorized` | HTTP integration | 401 without a token, 403 for a role without permission |
| TC-12 | Resolution rejection → re-resolution | `application/service/IncidentLifecycleInMemoryTest` | `lifecycleWithRejectResolution_clearsResolutionAndAllowsReResolution` | Service | Resolution cleared, back to IN_PROGRESS, can be resolved again |
| TC-12b | Reject resolution via service and HTTP | `application/service/IncidentServiceWorkflowTest` · `api/IncidentApiIntegrationTest` | `rejectResolution_happyPath_reopensToInProgress` · `rejectResolution_returnsToInProgress` | Service / HTTP | Status IN_PROGRESS after rejection |
| TC-13 | Information request → employee response, SLA clock paused while waiting | `application/service/IncidentServiceWorkflowTest` · `application/service/IncidentLifecycleInMemoryTest` · `domain/sla/SlaEvaluatorTest` | `requestAndRespondInfo_happyPath_transitionsAndPublishesEvents` · `lifecycleWithWaitingForUser_pausesSlaClockAndAccumulatesPauseDuration` · `evaluate_waitingForUserActivePause_extendsEffectiveResolutionDeadline` | Service / Unit | WAITING_FOR_USER ↔ IN_PROGRESS; paused time extends the resolution deadline |
| TC-14 | Audit trail is complete for a full lifecycle (and for escalation events) | `integration/AuditTrailIntegrationTest` · `integration/EscalationChainEndToEndTest` (**NEW**) | `fullIncidentLifecycle_generatesCompleteAuditTrail` · `monitorTicks_escalateP1ThroughAllLevels_withAuditAndRecipients` | Integration | One entry per action with timestamp, actor, action, previous/new status, description |
| TC-15 | Notification recipient rules: assignee + admins + reporter, actor excluded, no broadcast on close | `application/notification/NotificationRecipientResolverTest` · `application/notification/EscalationNotificationTest` | `assigned_assigneeAdminsReporter`, `resolved_adminsAndReporter`, `infoRequested_assigneeAdminsReporter`, `closed_noBroadcast`, `resolutionRejected_assigneeAndAdminsNotified_actorAndUnrelatedExcluded` · `level1_notifiesAdminAndTeamLead` | Unit | Only the relevant users receive each notification |
| TC-16 | Duplicate detection is advisory and exposes only safe fields | `application/service/DuplicateDetectionServiceTest` · `api/DuplicateDetectionControllerTest` | `tokenOverlap_withServiceCategoryBonus`, `closedIncidents_areIgnored`, `candidate_safeFieldsOnly` · `checkDuplicates_findsMatches`, `checkDuplicates_noMatch_returnsEmpty` | Service / Controller | Similar open incidents returned; none for no match; no description or reporter data |
| TC-17 | Smart technician recommendation ranking and authorization | `application/service/TechnicianRecommendationServiceTest` · `api/TechnicianRecommendationControllerTest` | `ranksTechniciansBySkillLevelDescendingWhenSkillBasedStrategyActive`, `ranksTechniciansByWorkloadAscendingWhenLoadBasedStrategyActive`, `filtersOutUnavailableTechnicians`, `throwsExceptionWhenActorIsNotAuthorizedToAssign` · `getRecommendationsReturnsRankedTechniciansForAdmin`, `getRecommendationsThrowsUnauthorizedForEmployee` | Service / Controller | Ranked candidates for administrators; employees rejected |
| TC-18 | Incident search and filtering, role-scoped | `application/service/IncidentSearchServiceTest` | `search_byPriorityAndStatus`, `search_byExactIncidentId`, `search_bySlaStatus_breached`, `technician_passingAnotherTechnicianId_returnsEmpty` | Service | Only matching incidents the caller may see |
| TC-19 | Authentication negative cases | `api/AuthSecurityIntegrationTest` | `login_wrongPassword_returns401`, `login_unknownUser_returns401`, `login_inactiveUser_returns401`, `register_weakPassword_returns400AndCreatesNoUser` | HTTP integration | 401 for bad credentials (same message), 400 for a weak password and no user created |
| TC-20 | Keyword classification suggests the seeded category; unknown and ambiguous input is safe | `domain/classification/KeywordClassifierTest` | `classify_representativeDescriptions_returnsExpectedSeededCategory`, `classify_unknownDescription_returnsEmpty`, `classify_ambiguousTie_returnsEmpty` | Unit | Correct category, or empty when unknown or tied |
| TC-21 | Missing SLA configuration fails before anything is saved or published | `application/service/IncidentServiceCreateTest` | `createIncident_missingSla_failsBeforeSaveOrPublish` | Service | `MissingSlaException`, no incident saved, no event published |

## Verification status

| Test class | Tests | Status |
|---|---|---|
| `integration/EscalationChainEndToEndTest` (NEW) | 4 | Passed |
| `application/notification/EscalationRecipientRulesTest` (NEW) | 2 | Passed |
| `api/EscalationHttpRbacIntegrationTest` (NEW) | 2 | Passed |

Full backend suite: `.\mvnw.cmd clean test` completed with all tests passing (0 failures, 0 errors). The suite had 830 tests before the 8 new tests above were added.