# IMPLEMENTATION_MANIFEST.md — I-ITSM Phase 1

Companion to `CONTRACT_FREEZE.md` (version 1.0). Section references (§) point there. If anything here conflicts with the Contract Freeze, **the Contract Freeze wins**; report the conflict instead of choosing.

## 0. Step zero — before the freeze commit (A, ~30 min)
1. **Rotate the Postgres password** that is in `application-local.yml`; treat it as exposed.
2. Repository history is still tiny: if the password was ever committed, rewrite history now (`git filter-repo --path backend/src/main/resources/application-local.yml --invert-paths`), force-push once, and tell B to re-clone. Do this **before** the freeze commit, never after.
3. Do the housekeeping rows (gate G-1) below, commit, push.
4. Commit the three freeze documents into `docs/`. B pulls. From this commit on, §W applies.

## 1. Files

Path prefixes: **M/** = `backend/src/main/java/com/iitsm/` · **T/** = `backend/src/test/java/com/iitsm/` · **R/** = `backend/src/main/resources/`. "Deps" lists what must already exist/compile; section references are to `CONTRACT_FREEZE.md`.

| File | Owner | Purpose | Dependencies |
|---|---|---|---|
| **Housekeeping (gate G-1)** | | | |
| `backend/pom.xml` | A | Spring Boot 3.5.x; only `starter-web`, `starter-validation`, `starter-test`; Java 21 | — |
| `.gitignore` (repo root) | A | Ignore `target/`, `.env`, `application-local.yml`, IDE files, `node_modules/` | — |
| `R/application.yaml` | A | Remove `spring.profiles.active: local`; keep `spring.application.name` | — |
| `R/application-local.yml` | A | **Delete** (`git rm`); rotate the exposed DB password; clean history before the freeze commit (see manifest §0) | — |
| `R/application-local.yml.example` | A | Placeholder-only template (`${DB_URL}`, `${DB_USER}`, `${DB_PASSWORD}`) for Phase 2 | — |
| `T/IitsmApplicationTests.java` | A | **Delete** (no Spring-context tests in Phase 1) | — |
| **Enums (gate G0)** | | | |
| `M/domain/model/Role.java`, `IncidentStatus.java`, `PriorityLevel.java`, `Impact.java`, `Urgency.java`, `BusinessCriticality.java`, `CategorySource.java`, `NoteType.java` | A | Enums exactly as §E | — |
| **Exceptions (gate G0)** | | | |
| `M/domain/exception/ItsmException.java` | A | Base unchecked exception with `getCode()` (§H) | — |
| `M/domain/exception/{InvalidInput,UnauthorizedOperation,InvalidIncident,IncidentNotFound,TechnicianUnavailable,TechnicianOverloaded,ServiceNotFound,UserNotFound,CategoryNotFound,MissingSla,DuplicateIncident}Exception.java` | A | 11 subclasses, `(String message)` constructor, fixed code | `ItsmException` |
| `M/domain/exception/InvalidStateTransitionException.java` | A | `(IncidentStatus, String action)`; getters | `ItsmException`, `IncidentStatus` |
| **Model (gate G1A)** | | | |
| `M/domain/model/User.java`, `Employee.java`, `Technician.java`, `Administrator.java` | A | User hierarchy (§D.1–D.4) | enums, `InvalidInputException` |
| `M/domain/model/ItService.java`, `Category.java` | A | Catalogue entities (§D.5–D.6) | enums, exceptions |
| `M/domain/model/SlaPolicy.java` | A | SLA policy record (§G.1) | `PriorityLevel`, exceptions |
| **Priority (gate G1A)** | | | |
| `M/domain/priority/PriorityInput.java`, `PriorityResult.java`, `PriorityStrategy.java` | A | Records + strategy interface (§F.1, §G.1) | enums, exceptions |
| `M/domain/priority/WeightedPriorityStrategy.java` | A | Weighted score + bands + thresholds (§K) | `PriorityStrategy` |
| **State machine (gate G1) — B** | | | |
| `M/domain/incident/IncidentState.java` | B | State interface (§F.3) | `IncidentStatus` |
| `M/domain/incident/AbstractIncidentState.java` | B | Default "throw `InvalidStateTransitionException`" / `false` behaviour | `IncidentState`, exception |
| `M/domain/incident/{New,Classified,Assigned,Acknowledged,InProgress,WaitingForUser,Resolved,Verified,Closed}State.java` | B | Nine package-private concrete states per §J | `AbstractIncidentState` |
| `M/domain/incident/IncidentStates.java` | B | `initial()`, `of(IncidentStatus)` | the 9 states |
| **Interfaces & records (gate G1A) — A** | | | |
| `M/domain/assignment/AssignmentStrategy.java`, `TechnicianScore.java` | A | Strategy contract (§F.2) | `Incident` signature (compiles after G2; write against §D.7), `Technician` |
| `M/domain/classification/CategoryClassifier.java`, `CategorySuggestion.java`, `NoOpClassifier.java` | A | Classification hook (§F.4, §O) | `CategorySource` |
| `M/domain/repository/IncidentRepository.java`, `IncidentSearchCriteria.java` | A | Incident persistence contract (§M) | `Incident` (G2 for compile), enums |
| `M/domain/repository/UserRepository.java`, `ItServiceRepository.java`, `CategoryRepository.java`, `SlaPolicyRepository.java` | A | Reference-data contracts (§M) | model |
| `M/domain/factory/IncidentIdGenerator.java` | A | Id seam (§F.6) | — |
| `M/domain/factory/UserDraft.java`, `UserFactory.java` | A | Polymorphic user creation (§N.2) | model, `Role` |
| **Incident aggregate (gate G2) — A** | | | |
| `M/domain/incident/IncidentNote.java`, `Resolution.java`, `Feedback.java`, `IncidentDraft.java` | A | Value records (§G.1) | model, exceptions |
| `M/domain/incident/Incident.java` | A | Aggregate root (§D.7) | B's `IncidentState`/`IncidentStates` (G1), `PriorityResult`, model, exceptions |
| `M/domain/factory/IncidentFactory.java` | A | Id + priority assembly (§N.1) | `Incident`, `IncidentDraft`, `PriorityStrategy`, `IncidentIdGenerator` |
| **In-memory persistence (gate G2) — A** | | | |
| `M/infrastructure/persistence/memory/InMemoryIncidentRepository.java` | A | §M, `synchronized`, `LinkedHashMap` | `IncidentRepository`, `Incident` |
| `M/infrastructure/persistence/memory/InMemoryUserRepository.java`, `InMemoryItServiceRepository.java`, `InMemoryCategoryRepository.java`, `InMemorySlaPolicyRepository.java` | A | §M | matching interfaces |
| `M/infrastructure/persistence/memory/InMemoryIncidentIdGenerator.java` | A | `INC1001…` via `AtomicLong` | `IncidentIdGenerator` |
| **Events (gate G1E) — B** | | | |
| `M/domain/sla/SlaType.java`, `SlaStatus.java` | B | Enums (§E) | — |
| `M/domain/event/DomainEvent.java`, `DomainEventListener.java`, `DomainEventPublisher.java` | B | Observer contracts (§P.1) | `IncidentStatus` |
| `M/domain/event/{IncidentCreated,IncidentClassified,PriorityChanged,IncidentAssigned,IncidentAcknowledged,IncidentStatusChanged,IncidentResolved,IncidentClosed,IncidentUpdated,AssignmentFailed,SlaWarning,SlaViolated}.java` | B | 12 event records (§P.1) | `DomainEvent`, enums, `SlaType` |
| `M/infrastructure/event/InMemoryDomainEventPublisher.java` | B | Synchronous publisher (§P.3) | `DomainEventPublisher`, `InvalidInputException` |
| **SLA — B** | | | |
| `M/domain/sla/SlaDeadlines.java`, `SlaEvaluation.java` | B | Result records (§G.1) | `SlaStatus` |
| `M/domain/sla/SlaPolicyResolver.java` | B | Override → default → `MissingSlaException` (§L.1) | `SlaPolicyRepository`, `SlaPolicy` (G1A) |
| `M/domain/sla/SlaCalculator.java` | B | Deadlines from policy (§L.3) | `SlaPolicyResolver` |
| `M/domain/sla/SlaEvaluator.java` | B | Pure breach evaluation (§L.4) | `Incident` getters (G2) |
| **Application — B** | | | |
| `M/application/command/{CreateIncident,AssignIncident,UpdatePriorityInputs,CloseIncident,SubmitFeedback}Command.java`, `IncidentTextCommand.java` | B | Six command records (§G.2) | enums, exceptions |
| `M/application/service/IncidentOperation.java` | B | 16-value enum (§Q.1) | — |
| `M/application/service/IncidentPermissionPolicy.java` | B | Role matrix (§Q.2) | model, `Incident`, exceptions |
| `M/application/service/IncidentService.java` | B | 19 operations (§Q.4) | everything above (needs G2) |
| **Test support — A (gate G2)** | | | |
| `T/support/TestClock.java`, `T/support/TestData.java` | A | §T.1 | model, `IncidentDraft` |
| **A tests (≥62)** | | | |
| `T/domain/model/UserHierarchyTest.java`, `CatalogEntitiesTest.java` | A | §T.2 | model |
| `T/domain/priority/WeightedPriorityStrategyTest.java` | A | §K oracle | priority |
| `T/domain/factory/IncidentFactoryTest.java`, `UserFactoryTest.java` | A | §T.2 | factories |
| `T/domain/incident/IncidentTest.java` | A | §T.2 | `Incident`, B's states |
| `T/infrastructure/persistence/memory/InMemoryIncidentRepositoryTest.java`, `InMemoryReferenceRepositoriesTest.java` | A | §T.2 | in-memory repos |
| `T/domain/classification/NoOpClassifierTest.java` | A | §T.2 | `NoOpClassifier` |
| **B tests (≥76)** | | | |
| `T/domain/incident/IncidentStateTransitionTest.java` | B | 99-pair matrix, guards | states |
| `T/domain/sla/SlaPolicyResolverTest.java`, `SlaCalculatorTest.java`, `SlaEvaluatorTest.java` | B | §T.2 | SLA, `TestClock`, `TestData` |
| `T/infrastructure/event/InMemoryDomainEventPublisherTest.java` | B | §T.2 | publisher |
| `T/application/service/IncidentPermissionPolicyTest.java` | B | §T.2 | policy |
| `T/application/service/IncidentServiceCreateTest.java`, `IncidentServiceAssignmentTest.java`, `IncidentServiceWorkflowTest.java`, `IncidentServiceQueryAndPriorityTest.java` | B | §T.2 | service, in-memory repos |
| `T/application/service/ServiceFixture.java`, `StubAssignmentStrategy.java`, `RecordingListener.java` | B | B-only test helpers | service |
| `T/application/service/IncidentLifecycleInMemoryTest.java` | B | Complete in-memory lifecycle + events + SLA `MET` | all |
| **Docs** | | | |
| `docs/api-contract.md` | A | §U.1 expanded | — |
| `docs/priority-design.md` | A | §K expanded | — |
| `docs/sla-and-lifecycle-design.md` | B | §J + §L expanded | — |
| `docs/ai-log-A.md`, `docs/ai-log-B.md` | A / B | AI usage log | — |

## 2. Gates (build order)
A gate is "pushed to `main`, `mvn -q test` green". Nobody starts a task that depends on a gate before it is passed.

| Gate | Who | Contents | Unblocks |
|---|---|---|---|
| **G-1** | A | Housekeeping rows | everything |
| **G0** | A | 8 enums + 13 exceptions | all of B's work; A's model |
| **G1** | B | `IncidentState`, `AbstractIncidentState`, 9 states, `IncidentStates`, `IncidentStateTransitionTest` | A's `Incident` |
| **G1A** | A | model (7), priority (4), assignment/classification/repository/factory interfaces & drafts | B's SLA resolver/calculator, B's service signatures |
| **G1E** | B | events (15 files), `SlaType`, `SlaStatus`, in-memory publisher + test | — (parallel) |
| **G2** | A | `Incident` + records, `IncidentFactory`, in-memory repos + id generator, `TestClock`, `TestData`, A's tests | B's `SlaEvaluator`, commands tests, `IncidentService` |
| **G3** | B | SLA classes + tests, commands, `IncidentOperation`, `IncidentPermissionPolicy`, `IncidentService` + tests | — |
| **G4** | both | `IncidentLifecycleInMemoryTest` green; each reads the other's code; docs from §U done | Phase 2 |

Parallelism: after G0, A does G1A while B does G1 then G1E. After G1, A does G2 while B does the SLA resolver/calculator and commands. B's service work starts when G2 lands.

## 3. AI coding session briefs (one module per session — AGENTS.md)
Give each session: `CONTRACT_FREEZE.md`, this manifest, `AGENTS.md`, and the brief below. Standing rules for every session: no Lombok, no Spring annotations, no new dependency, no files outside the brief, `Clock`/`Instant` only (never `now()`), tests written **from the contract**, report files changed, do not claim tests pass unless run, ask before deviating.

| Session | Owner | Task | Read first | Must not touch |
|---|---|---|---|---|
| **A0** | A | Housekeeping rows | §A.3, AGENTS.md | any Java |
| **A1** | A | Enums (§E) + exceptions (§H) | §E, §H | everything else |
| **A2** | A | Model (§D.1–D.6), `SlaPolicy`, priority package (§K), tests `UserHierarchyTest`, `CatalogEntitiesTest`, `WeightedPriorityStrategyTest` | §D, §G.1, §K | `incident`, `sla`, `event` |
| **A3** | A | Interfaces & drafts: `AssignmentStrategy`, `TechnicianScore`, classification package + test, repository interfaces, `IncidentIdGenerator`, `UserDraft`, `UserFactory` + test | §F, §M, §N.2, §O | `Incident` |
| **A4** | A | `Incident`, `IncidentNote`, `Resolution`, `Feedback`, `IncidentDraft`, `IncidentFactory`, `IncidentTest`, `IncidentFactoryTest` (needs G1) | §D.7, §G.1, §J, §N.1 | B's state files |
| **A5** | A | In-memory repos + id generator + tests, `TestClock`, `TestData` | §M, §T.1 | domain interfaces |
| **A6** | A | `docs/api-contract.md`, `docs/priority-design.md` | §K, §U | code |
| **B1** | B | State pattern (§D.8, §F.3, §J) + `IncidentStateTransitionTest` (needs G0) | §D.8, §F.3, §J | `Incident.java` |
| **B2** | B | Events + publisher + test (§P) | §P | service |
| **B3** | B | SLA package + tests (§L) (resolver/calculator after G1A, evaluator after G2) | §L | `Incident.java` |
| **B4** | B | Commands, `IncidentOperation`, `IncidentPermissionPolicy` + test | §G.2, §Q.1–Q.2 | repositories |
| **B5** | B | `IncidentService` + 4 service test classes + helpers (needs G2) | §Q | domain classes |
| **B6** | B | `IncidentLifecycleInMemoryTest`, `docs/sla-and-lifecycle-design.md` | §T.2, §J, §L | — |

## 4. Definition of done (Phase 1)
* `cd backend && mvn -q test` passes with ≥138 test methods (A ≥ 62, B ≥ 76) and no Spring context.
* `grep -rE "org\.springframework|jakarta\.|lombok" backend/src/main/java/com/iitsm/{domain,application,infrastructure}` returns nothing.
* `grep -rE "Instant\.now|LocalDateTime\.now|new Date\(" backend/src/main/java` returns nothing.
* Illegal transition (`NEW → close`) throws `InvalidStateTransitionException`; an incident created through `IncidentService` has a priority and both SLA deadlines.
* Each developer has read the other's code before G4 (Plan: both must be able to explain the whole core on video).
* No files changed outside the manifest.
