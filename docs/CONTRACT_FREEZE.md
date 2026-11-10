# CONTRACT_FREEZE.md — I-ITSM Phase 1 (Domain Core)

| | |
|---|---|
| **Status** | FROZEN when committed to `main` |
| **Version** | 1.0 (2026-11-09) |
| **Project** | Intelligent IT Service Management & Incident Resolution System |
| **Base package** | `com.iitsm` (Java 21, Maven) |
| **Developers** | **A** = project owner (model, exceptions, priority, factories, repositories, persistence, classifier hook). **B** = teammate (State pattern, SLA, IncidentService, domain events) |
| **Source priority** | 1) Official spec (`OOP_S1_2026-2027_Project.pdf`) 2) rubric (in spec) 3) codebase 4) team decisions 5) Project Plan PDF / old AI plans |

Every name, type, signature and rule in this document is binding. Where this document is silent, the coding session must **ask, not invent**.

---

## 0. Decision log

### 0.1 Decisions taken (from the questionnaires)

| ID | Decision |
|---|---|
| Q-M2 | Spring Boot **3.5.x** (downgrade the pom; docs already say 3.5.x). |
| Q-M3 | Phase 1 pom keeps only `spring-boot-starter-web`, `spring-boot-starter-validation`, `spring-boot-starter-test`. JPA, Flyway, Security, PostgreSQL, H2 and the H2 console are removed and re-added in Phase 2. |
| Q-M4 | Phase 1 = domain + application services + in-memory repositories + tests + docs. **No controllers, no Spring annotations in domain/application/infrastructure.** API is documented, not built. |
| Q-M5, Q-N8 | Permissions enforced in the application layer. Every service method takes an `actorId`; `IncidentPermissionPolicy` holds the role matrix. |
| Q-M6 | `User` (abstract) → `Employee`, `Technician`, `Administrator`. `Role` enum has 5 values; `TEAM_LEAD` is held by a `Technician`, `IT_MANAGER` by an `Administrator`. |
| Q-M7 | `Category` is a class (admin-extensible), not an enum. |
| Q-M8 | Reporter supplies Impact and Urgency; Business Criticality comes from the `ItService`. |
| Q-M9 | Impact/Urgency/BusinessCriticality are enums with `weight()` 1–4. |
| Q-M10 | `WAITING_FOR_USER` is added; the resolution SLA clock pauses in it. |
| Q-M11 | Default SLA per priority + optional per-service override. |
| Q-M12/13 | Transition table in §J; role matrix in §Q.2. |
| Q-M14 | Affected-users bump in bands (+0/+4/+8/+12), added after multiplication. |
| Q-M15 | Priority recalculated when impact/urgency are updated (admin, or assigned technician). |
| Q-M16 | SLA: 24×7, clock starts at creation, response met at first acknowledge, resolution met at RESOLVED, exactly-at-deadline is **not** a breach, warning when ≤20 % of the target remains, pause in `WAITING_FOR_USER`. |
| Q-M17/18 | `AssignmentStrategy.rank(...)` + default `pick(...)`; technician workload is computed, never stored. |
| Q-M19 | Auto-assign on create. Manual assign throws on unavailable/overloaded. Auto-assign with no candidate leaves the incident `CLASSIFIED` and publishes `AssignmentFailed`. |
| Q-M20/21 | Repository interfaces in `domain.repository`; Phase 2 JPA uses separate JPA entities + mappers. |
| Q-M22 | IDs `INC1001`, `INC1002`, … via `IncidentIdGenerator`. |
| Q-M23, Q-N5(b) | Synchronous in-process `DomainEventPublisher` + `DomainEventListener` + `InMemoryDomainEventPublisher`. |
| Q-M25 | Non-critical listener failures are caught and logged; `critical()` listeners propagate. |
| Q-M27 | One class per state (`IncidentState`), `IncidentStatus` enum for storage/API. |
| Q-M29 | Error JSON `{timestamp, status, code, message, details}`. |
| Q-M30 | Plain JUnit 5 + Mockito, no Spring context, no `@SpringBootTest`; `contextLoads` test deleted. |
| Q-N1(b) | `Incident` and all state classes share package `com.iitsm.domain.incident`; mutators are package-private or guarded; states are package-private. |
| Q-N2(a) | `IncidentService.createIncident` (B) computes SLA deadlines; `IncidentFactory` knows nothing about SLA. |
| Q-N3(a) | Phase 1 = `AssignmentStrategy` contract + manual assign + `autoAssignIncident` tested with a stub strategy. Skill/load strategies are Phase 2. |
| Q-N4(a) | No `Notification`/`AuditEntry` classes in Phase 1; events carry all 7 audit fields so Phase 2 adds only listeners. |
| Q-N6(a) | `IncidentFactory` + `UserFactory` in Phase 1. `NotificationFactory` arrives with `Notification` in Phase 2. |
| Q-N7(a) | Commands (IDs only) in `application.command`; `IncidentDraft` (resolved objects) in the domain. |
| Q-S1…S10 | Defaults accepted. Detail: §C, §U. |
| Q-N13 | A = project owner, B = teammate. Work directly on `main`; `git pull --rebase origin main` before every push; owned files are never edited by the other developer; frozen files change only by agreement. |

### 0.2 Refinements made while freezing (they override my earlier recommendations)

| # | Earlier recommendation | Final | Reason |
|---|---|---|---|
| R1 | `DomainEvent` sealed | Plain interface | Sealed would force editing a frozen file for every Phase 3 event. |
| R2 | `IncidentDraft` in `domain.factory` | `domain.incident` | `Incident`'s constructor takes it; keeping it in `factory` would create a package cycle. |
| R3 | 8-field search incl. SLA status in repository | Repository filters 7 criteria (8 fields); the SLA-status filter moves to a Phase 2 search service | SLA status depends on `Clock` + `SlaEvaluator`, which a repository must not know. |
| R4 | Separate `AssignmentService`, `IncidentSearchService` | Single `IncidentService` in Phase 1 | Fewer files; search service is Phase 2. |
| R5 | CoR interface stub, `ReportNarrator`, `DuplicateDetector`, `SlaRiskDetector` | **Not created** | Your freeze prompt: no patterns "merely for demonstration". They are pure additions later. |
| R6 | `IncidentFactory` takes a `Clock` | Takes `Instant createdAt` | One time read per operation; deterministic. |
| R7 | `MissingSlaException(serviceId, priority)` | Single `(String message)` constructor | Same exception is reused for "incident has no deadlines". |
| R8 | `SlaDeadlines` shared | B-internal; `Incident.applySlaDeadlines(Instant, Instant)` takes two `Instant`s | Avoids an A→B dependency. |

---

## A. Scope

### A.1 Phase 1 goals
1. A framework-free, fully tested domain core that satisfies the Plan's done-criterion: *an illegal transition returns a clear error, and an incident created in memory gets a priority and an SLA deadline.*
2. Contracts stable enough that Phase 2 (Spring Security/JWT, JPA/Flyway/PostgreSQL, REST, audit, notifications, strategies) and Phase 3 (intelligent features, dashboard) are **new files only**.
3. Two developers working in parallel on disjoint files.

### A.2 Included requirements (spec → Phase 1)
| Spec requirement | Phase 1 deliverable |
|---|---|
| User management (3 roles) | `User` hierarchy, `Role`, `UserFactory`, `UserRepository` |
| Incident create/view/update/assign/resolve/close | `IncidentService` (§Q) |
| Priority: impact, urgency, criticality, affected users, service | `PriorityStrategy`, `WeightedPriorityStrategy` (§K) |
| ≥2 assignment strategies | `AssignmentStrategy` contract + manual/auto assignment path (strategies: Phase 2) |
| SLA: calculation, deadlines, violation detection | `SlaPolicyResolver`, `SlaCalculator`, `SlaEvaluator` (§L); recording/warnings: Phase 3 |
| Lifecycle + invalid transitions rejected | State pattern (§J) |
| Audit (7 fields) | Events carry all 7 fields (§P); `AuditEntry` + listener: Phase 2 |
| Notifications (≥2 mechanisms) | Events carry recipients' data; channels: Phase 2 |
| Search ≥5 criteria | `IncidentRepository.search(IncidentSearchCriteria)` — 7 criteria |
| Persistence | Repository interfaces + in-memory implementations |
| Exceptions | 13 custom exceptions (§H) |
| ≥2 design patterns | State, Strategy, Observer, Repository, Factory |
| ≥15 tests | ≥138 test methods required (§T) |
| Intelligent features (≥2) | Hooks only: `CategoryClassifier`, `AssignmentStrategy.rank`, evaluator reusable for SLA risk |

### A.3 Explicit exclusions (Phase 1 must NOT contain)
Controllers/DTOs/`@RestController`; Spring Security/JWT/BCrypt/login/register; JPA entities, Flyway, PostgreSQL/H2 config; springdoc; Lombok; any new Maven dependency; notifications and `AuditEntry`; concrete assignment strategies; admin CRUD services; reports/dashboard; schedulers/`@Scheduled`/SLA monitor; SLA-violation recording; classifier implementations beyond `NoOpClassifier`; duplicate detection; escalation; SSE; frontend.

### A.4 Assumptions
1. Today is 2026-11-09; the Plan's Phase 1 dates (24–29 Oct) are already missed. **Recommended re-baseline (not binding):** Phase 1 complete by 11 Nov; Phase 2 12–17 Nov; Phase 3 must-haves only afterwards; spec hard deadline is 22 Nov.
2. SLA clock is 24×7 (no business hours).
3. Time = `java.time.Instant`/`Duration`; every class needing "now" receives a `Clock` (services) or an `Instant` argument (domain).
4. In-memory repositories store and return **the same object references** (mutations are visible without `save`; services always call `save` anyway so Phase 2 behaves identically).
5. Spec escalation step "System Administrator" is played by `Role.ADMINISTRATOR`; "IT Manager" by `Role.IT_MANAGER`; "Team Lead" by `Role.TEAM_LEAD`.
6. The Spec's video-file naming (`…2025-2026…`) is confirmed with the instructor later; irrelevant to Phase 1.

---

## B. Architecture

### B.1 Layers and responsibilities
| Layer | Package | Responsibility | May depend on |
|---|---|---|---|
| Domain | `com.iitsm.domain.*` | Entities, rules, state machine, priority, SLA, events, repository **interfaces**, strategy interfaces, exceptions. **No Spring, no JPA, no Jackson.** | JDK only |
| Application | `com.iitsm.application.*` | Use-case orchestration (`IncidentService`), commands, permission policy. Business rules are *not* here, only sequencing. | domain |
| Infrastructure | `com.iitsm.infrastructure.*` | Implementations of domain interfaces (in-memory repos, id generator, event publisher). | domain |
| API | `com.iitsm.api` | **Not created in Phase 1** (controllers, DTOs, error handler). | application, domain |
| Config | `com.iitsm.config` | **Not created in Phase 1** (bean wiring, security, JPA). | everything |

### B.2 Dependency direction
```
api ──► application ──► domain ◄── infrastructure
  └────────── config wires all ──────────┘
```
Package-level order inside `domain` (arrows = "depends on"; **no cycles allowed**):
```
model            (no dependencies)
exception   ──►  model
priority    ──►  model
incident    ──►  model, exception, priority
repository  ──►  model, incident
assignment  ──►  model, incident
classification ► (JDK only)
factory     ──►  model, incident, priority, exception
sla         ──►  model, incident, repository, exception
event       ──►  model, sla
```

### B.3 Rules
1. Domain classes contain no `org.springframework.*`, `jakarta.*`, no Lombok.
2. Business rules live in domain classes; `IncidentService` only loads, checks permission, calls the domain, saves, publishes.
3. Only `IncidentService` publishes events, **after** `save`.
4. No `static` mutable state; no `LocalDateTime.now()` / `Instant.now()` / `new Date()` anywhere in `src/main` except inside `Clock` usage in `IncidentService`.
5. All nullable getters return `Optional`; collections returned are unmodifiable.

---

## C. Exact package / file structure

`backend/src/main/java/com/iitsm/`
```
IitsmApplication.java                         (existing, unchanged)
domain/
  model/            Role IncidentStatus PriorityLevel Impact Urgency BusinessCriticality CategorySource NoteType
                    User Employee Technician Administrator ItService Category SlaPolicy
  exception/        ItsmException InvalidInputException UnauthorizedOperationException InvalidIncidentException
                    IncidentNotFoundException InvalidStateTransitionException TechnicianUnavailableException
                    TechnicianOverloadedException ServiceNotFoundException UserNotFoundException
                    CategoryNotFoundException MissingSlaException DuplicateIncidentException
  priority/         PriorityInput PriorityResult PriorityStrategy WeightedPriorityStrategy
  incident/         Incident IncidentNote Resolution Feedback IncidentDraft            (A)
                    IncidentState AbstractIncidentState IncidentStates                 (B)
                    NewState ClassifiedState AssignedState AcknowledgedState InProgressState
                    WaitingForUserState ResolvedState VerifiedState ClosedState        (B, package-private)
  sla/              SlaType SlaStatus SlaDeadlines SlaEvaluation SlaPolicyResolver SlaCalculator SlaEvaluator
  assignment/       AssignmentStrategy TechnicianScore
  classification/   CategoryClassifier CategorySuggestion NoOpClassifier
  event/            DomainEvent DomainEventPublisher DomainEventListener
                    IncidentCreated IncidentClassified PriorityChanged IncidentAssigned IncidentAcknowledged
                    IncidentStatusChanged IncidentResolved IncidentClosed IncidentUpdated AssignmentFailed
                    SlaWarning SlaViolated
  factory/          UserDraft IncidentIdGenerator IncidentFactory UserFactory
  repository/       IncidentRepository IncidentSearchCriteria UserRepository ItServiceRepository
                    CategoryRepository SlaPolicyRepository
application/
  command/          CreateIncidentCommand AssignIncidentCommand UpdatePriorityInputsCommand
                    IncidentTextCommand CloseIncidentCommand SubmitFeedbackCommand
  service/          IncidentOperation IncidentPermissionPolicy IncidentService
infrastructure/
  persistence/memory/  InMemoryIncidentRepository InMemoryUserRepository InMemoryItServiceRepository
                       InMemoryCategoryRepository InMemorySlaPolicyRepository InMemoryIncidentIdGenerator
  event/            InMemoryDomainEventPublisher
```
`backend/src/test/java/com/iitsm/` mirrors the packages, plus `support/` (§T). `api/` and `config/` are **not created** until Phase 2 (Git cannot track empty packages and no placeholders are allowed).

Repository root: `docs/` (§U), root `.gitignore`.

---

## D. Domain model (classes)

Conventions: all validation failures throw `InvalidInputException` unless stated. "Blank" = null or empty after `trim()`. Strings are stored trimmed. Constructors are fully validating. No public setters anywhere.

### D.1 `com.iitsm.domain.model.User` — `public abstract class User`
Responsibility: identity and role of a person; base of the user hierarchy (true is-a).
```java
private final String id;  private String name;  private String email;
private final Role role;  private boolean active = true;

protected User(String id, String name, String email, Role role)
public final String getId();   public final String getName();   public final String getEmail();
public final Role getRole();   public final boolean isActive();
public final void activate();  public final void deactivate();
@Override public final boolean equals(Object o)   // by id and runtime class
@Override public final int hashCode()             // by id
@Override public String toString()                // "<ROLE> <id>"
```
Invariants: `id`, `name` non-blank; `email` has exactly one `@` with non-blank text on both sides; `role` non-null.

### D.2 `Employee extends User` — `public final class Employee`
```java
private final String department;
public Employee(String id, String name, String email, String department)   // role = Role.EMPLOYEE
public String getDepartment()
```
Invariant: `department` non-blank.

### D.3 `Technician extends User` — `public final class Technician`
```java
private final Map<String,Integer> skillLevels;   // key = Category.id, value = proficiency 1..5
private final int yearsOfExperience;  private final int maxActiveIncidents;  private boolean available = true;

public Technician(String id, String name, String email, Role role,
                  Map<String,Integer> skillLevels, int yearsOfExperience, int maxActiveIncidents)
public Map<String,Integer> getSkillLevels()          // unmodifiable copy
public int skillLevelFor(String categoryId)          // 0 if absent
public boolean hasSkill(String categoryId)           // skillLevelFor(...) > 0
public int getYearsOfExperience()
public int getMaxActiveIncidents()
public boolean isAvailable()
public void markAvailable();  public void markUnavailable()
public boolean isAssignable()                        // isActive() && isAvailable()
public boolean isOverloaded(int activeIncidents)     // activeIncidents >= maxActiveIncidents
```
Invariants: `role ∈ {TECHNICIAN, TEAM_LEAD}`; `skillLevels` non-null (may be empty), keys non-blank, values 1..5; `yearsOfExperience ≥ 0`; `maxActiveIncidents ≥ 1`. Workload is **never stored** here.

### D.4 `Administrator extends User` — `public final class Administrator`
```java
public Administrator(String id, String name, String email, Role role)   // role ∈ {ADMINISTRATOR, IT_MANAGER}
```
No extra fields in Phase 1 (type exists because the spec names Administrator as a user category and for polymorphic creation).

### D.5 `ItService` — `public final class ItService`
Responsibility: an IT service that can be affected; supplies business criticality.
```java
private final String id;  private final String name;  private final BusinessCriticality businessCriticality;
public ItService(String id, String name, BusinessCriticality businessCriticality)
public String getId();  public String getName();  public BusinessCriticality getBusinessCriticality()
```
Invariants: all non-null/non-blank. equals/hashCode by id.

### D.6 `Category` — `public final class Category`
Responsibility: admin-extensible incident category (replaces a hard-coded enum). `id` is the stable code (e.g. `"NETWORK"`).
```java
private final String id;  private final String name;  private boolean active = true;
public Category(String id, String name)
public String getId();  public String getName();  public boolean isActive();
public void activate();  public void deactivate()
```
Invariants: `id` matches `[A-Z][A-Z0-9_]*`, `name` non-blank. equals/hashCode by id. The nine spec categories (`HARDWARE, SOFTWARE, NETWORK, DATABASE, CLOUD, SECURITY, AUTHENTICATION, APPLICATION, STORAGE`) are seed data (test support now, Flyway in Phase 2), not code constants.

### D.7 `com.iitsm.domain.incident.Incident` — `public final class Incident` (A)
Responsibility: the aggregate root. Owns its data, enforces invariants, delegates *which transitions are legal* to its `IncidentState`. Composition: owns its `IncidentNote`s, `Resolution`, `Feedback`. Association/aggregation: references `User` (reporter), `ItService`, `Category`, `Technician`.

Fields (all private):
```java
final String id;  final String title;  final String description;
final User reporter;  final ItService service;
Category category;  Category suggestedCategory /*nullable*/;  CategorySource categorySource;
Impact impact;  Urgency urgency;  final int affectedUsers;
PriorityLevel priority;  int priorityScore;
final Instant createdAt;  IncidentState state;                       // never null
Technician assignedTechnician /*nullable*/;  Instant assignedAt /*nullable*/;
Instant acknowledgedAt /*nullable; FIRST acknowledgement only*/;
Instant verifiedAt /*nullable*/;  Instant closedAt /*nullable*/;  String closedById /*nullable*/;  String closureNote /*nullable*/;
Instant responseDeadline /*nullable until applied*/;  Instant resolutionDeadline /*nullable until applied; base, excludes pauses*/;
Instant waitingSince /*nullable*/;  Duration pausedDuration = Duration.ZERO;
final List<IncidentNote> notes = new ArrayList<>();
Resolution resolution /*nullable*/;  Feedback feedback /*nullable*/;
```
Constructor (public — `IncidentFactory` lives in another package; the constructor cannot accept a state, so the state machine cannot be bypassed):
```java
public Incident(String id, IncidentDraft draft, PriorityResult priority, Instant createdAt)
```
Postconditions: status `NEW`, no assignee, no deadlines, no notes. Invariants: `id` non-blank; `draft`, `priority`, `createdAt` non-null.

Getters (exact):
```java
public String getId();  public String getTitle();  public String getDescription();
public User getReporter();  public ItService getService();
public Category getCategory();  public Optional<Category> getSuggestedCategory();  public CategorySource getCategorySource();
public Impact getImpact();  public Urgency getUrgency();  public int getAffectedUsers();
public PriorityLevel getPriority();  public int getPriorityScore();
public Instant getCreatedAt();  public IncidentStatus getStatus();            // = state.status()
public Optional<Technician> getAssignedTechnician();
public Optional<Instant> getAssignedAt();  public Optional<Instant> getAcknowledgedAt();
public Optional<Instant> getResolvedAt();                                        // = resolution.resolvedAt()
public Optional<Instant> getVerifiedAt();  public Optional<Instant> getClosedAt();
public Optional<String> getClosedById();   public Optional<String> getClosureNote();
public Optional<Instant> getResponseDeadline();  public Optional<Instant> getResolutionDeadline();
public Optional<Instant> getWaitingSince();  public Duration getPausedDuration();
public List<IncidentNote> getNotes();                                            // unmodifiable
public Optional<Resolution> getResolution();  public Optional<Feedback> getFeedback();
```
Behaviour methods. **Rule for every method:** (1) validate arguments → `InvalidInputException`; (2) ask the state / check guards → `InvalidStateTransitionException` or `InvalidIncidentException`; (3) only then mutate. A failed call never leaves the incident partly modified.

| Signature | Effect |
|---|---|
| `void applySlaDeadlines(Instant responseDeadline, Instant resolutionDeadline)` | Sets both deadlines. Both non-null, `createdAt ≤ response ≤ resolution`. No state guard (service-only caller). May be called again after a priority change. |
| `void classify()` | `state = state.classify()` |
| `void ensureAssignable()` | Calls `state.assign()` and discards the result (pure check). |
| `void ensureUnassignable()` | Calls `state.unassign()` and discards the result. |
| `void assign(Technician technician, Instant now)` | `next = state.assign()`; sets `assignedTechnician`, `assignedAt = now`; `state = next`. |
| `void unassign()` | `next = state.unassign()`; clears `assignedTechnician` and `assignedAt` (`acknowledgedAt` is **kept**); `state = next`. |
| `void acknowledge(Instant now)` | `next = state.acknowledge()`; `acknowledgedAt` set only if still null; `state = next`. |
| `void startWork()` | `state = state.startWork()` |
| `void requestInfo(String authorId, String question, Instant now)` | `next = state.requestInfo()`; `waitingSince = now`; adds note `INFO_REQUEST`; `state = next`. |
| `void receiveInfo(String authorId, String response, Instant now)` | `next = state.receiveInfo()`; `pausedDuration += max(0, between(waitingSince, now))`; `waitingSince = null`; adds note `INFO_RESPONSE`; `state = next`. |
| `void resolve(String resolverId, String summary, Instant now)` | `next = state.resolve()`; `resolution = new Resolution(resolverId, summary, now)`; `state = next`. |
| `void rejectResolution()` | `next = state.rejectResolution()`; `resolution = null`; `state = next`. |
| `void verify(Instant now)` | `next = state.verify()`; `verifiedAt = now`; `state = next`. |
| `void close(String closedById, String closureNote, Instant now)` | `next = state.close()`; sets `closedById`, `closureNote` (nullable → stored as null if blank), `closedAt = now`; `state = next`. |
| `void addInvestigationNote(String authorId, String text, Instant now)` | Requires `state.allowsInvestigationNotes()` else `InvalidIncidentException`; adds note `INVESTIGATION`. |
| `void addAdditionalInfo(String authorId, String text, Instant now)` | Requires `state.allowsAdditionalInfo()` else `InvalidIncidentException`; adds note `ADDITIONAL_INFO`. |
| `void submitFeedback(String authorId, int rating, String comment, Instant now)` | Requires `state.allowsFeedback()` and no existing feedback, else `InvalidIncidentException`; sets `Feedback`. |
| `void updatePriority(Impact impact, Urgency urgency, PriorityResult result)` | Requires `state.allowsPriorityChange()` else `InvalidIncidentException`; replaces impact, urgency, `priority`, `priorityScore`. |

Text arguments (`question`, `response`, `summary`, `text`) must be non-blank, ≤ 4000 chars. `authorId`/`resolverId`/`closedById` non-blank. Rating 1..5; `comment` nullable, ≤ 1000 chars.

### D.8 Incident state classes (B) — package `com.iitsm.domain.incident`
Interface `IncidentState` and `IncidentStates`: see §F.3. `AbstractIncidentState` (package-private abstract class): every transition method throws `new InvalidStateTransitionException(status(), "<methodName>")`; every `allows…` predicate returns `false`. Each concrete state is a **package-private `final` class with a private/shared singleton**, overrides `status()` and only the transitions/predicates listed in §J. States are stateless and are obtained only through `IncidentStates`.

### D.9 Other classes (specified in their own sections)
`WeightedPriorityStrategy` §K · `SlaPolicyResolver`, `SlaCalculator`, `SlaEvaluator` §L · `InMemory*Repository`, `InMemoryIncidentIdGenerator` §M · `IncidentFactory`, `UserFactory` §N · `NoOpClassifier` §O · `InMemoryDomainEventPublisher` §P · `IncidentService`, `IncidentPermissionPolicy` §Q.

---

## E. Enums

All in `com.iitsm.domain.model` unless stated. Names and order are frozen.

| Enum | Values (in order) | Extra members |
|---|---|---|
| `Role` | `EMPLOYEE, TECHNICIAN, TEAM_LEAD, ADMINISTRATOR, IT_MANAGER` | — |
| `IncidentStatus` | `NEW, CLASSIFIED, ASSIGNED, ACKNOWLEDGED, IN_PROGRESS, WAITING_FOR_USER, RESOLVED, VERIFIED, CLOSED` | `boolean countsTowardWorkload()` → true for `ASSIGNED, ACKNOWLEDGED, IN_PROGRESS, WAITING_FOR_USER` |
| `PriorityLevel` | `P1, P2, P3, P4` (P1 = Critical … P4 = Low) | — |
| `Impact` | `LOW(1), MEDIUM(2), HIGH(3), CRITICAL(4)` | `int weight()` |
| `Urgency` | `LOW(1), MEDIUM(2), HIGH(3), CRITICAL(4)` | `int weight()` |
| `BusinessCriticality` | `LOW(1), MEDIUM(2), HIGH(3), CRITICAL(4)` | `int weight()` |
| `CategorySource` | `MANUAL, KEYWORD, LLM` | — |
| `NoteType` | `INVESTIGATION, INFO_REQUEST, INFO_RESPONSE, ADDITIONAL_INFO` | — |
| `SlaType` (`domain.sla`, B) | `RESPONSE, RESOLUTION` | — |
| `SlaStatus` (`domain.sla`, B) | `ON_TRACK, WARNING, BREACHED, MET` | — |

Three separate 1–4 enums are intentional (compile-time prevention of swapped arguments).

---

## F. Interfaces

### F.1 `PriorityStrategy` — `com.iitsm.domain.priority` (A)
Purpose: swap the priority algorithm without touching callers (Strategy).
```java
PriorityResult calculate(PriorityInput input);        // throws InvalidInputException if input == null
```
Implementations: `WeightedPriorityStrategy` (Phase 1). Reason: spec allows "own prioritization algorithm"; Phase 3 may add history-based.

### F.2 `AssignmentStrategy` — `com.iitsm.domain.assignment` (A)
Purpose: interchangeable technician selection **and** the basis for "suggested technician" (Strategy).
```java
String name();                                                         // e.g. "skill-based"
List<TechnicianScore> rank(Incident incident,
                           List<Technician> candidates,
                           Map<String,Integer> activeWorkloadByTechnicianId);   // best first; may omit unsuitable technicians
default Optional<Technician> pick(Incident incident, List<Technician> candidates,
                                  Map<String,Integer> activeWorkloadByTechnicianId) {
    return rank(incident, candidates, activeWorkloadByTechnicianId).stream().findFirst().map(TechnicianScore::technician);
}
```
Contract: `candidates` are pre-filtered by the service (assignable and not overloaded); the workload map has an entry for every candidate; implementations never mutate arguments; return list is unmodifiable and sorted by descending score. Implementations: none in Phase 1 (test stub only); Phase 2: skill-based, load-based; later a `SwitchableAssignmentStrategy` that implements this same interface.

### F.3 `IncidentState` — `com.iitsm.domain.incident` (B; public interface)
Purpose: State pattern. Every transition method is **pure**: it returns the next state or throws; it never mutates anything.
```java
IncidentStatus status();
IncidentState classify();         IncidentState assign();           IncidentState unassign();
IncidentState acknowledge();      IncidentState startWork();        IncidentState requestInfo();
IncidentState receiveInfo();      IncidentState resolve();          IncidentState rejectResolution();
IncidentState verify();           IncidentState close();
boolean allowsInvestigationNotes();   boolean allowsAdditionalInfo();
boolean allowsFeedback();             boolean allowsPriorityChange();
```
Illegal call → `InvalidStateTransitionException(status(), "<methodName>")`. `IncidentStates` (B, public final class, private constructor):
```java
public static IncidentState initial();                    // the NEW state
public static IncidentState of(IncidentStatus status);    // for Phase 2 reconstitution; total over all 9 values
```

### F.4 `CategoryClassifier` — `com.iitsm.domain.classification` (A)
```java
Optional<CategorySuggestion> classify(String title, String description);
```
Purpose: category-suggestion hook (Strategy; Phase 3 adds keyword/LLM/fallback). Empty = "no suggestion". Must not throw for blank input (returns empty). Implementation Phase 1: `NoOpClassifier`.

### F.5 Repository interfaces — `com.iitsm.domain.repository` (A) — see §M.

### F.6 `IncidentIdGenerator` — `com.iitsm.domain.factory` (A)
```java
String nextIncidentId();      // "INC1001", "INC1002", ... never repeats
```
Reason: seam so Phase 2 can use a DB sequence.

### F.7 `DomainEventPublisher`, `DomainEventListener`, `DomainEvent` — `com.iitsm.domain.event` (B) — see §P.

---

## G. Records / commands (exact fields)

Records validate in the compact constructor and throw `InvalidInputException`.

### G.1 Domain records
| Record (package) | Fields | Validation |
|---|---|---|
| `IncidentNote` (`incident`, A) | `String authorId, NoteType type, String text, Instant createdAt` | all non-null/non-blank |
| `Resolution` (`incident`, A) | `String resolverId, String summary, Instant resolvedAt` | all non-null/non-blank |
| `Feedback` (`incident`, A) | `String authorId, int rating, String comment, Instant submittedAt` | `1 ≤ rating ≤ 5`; `comment` nullable (null → `""`) |
| `IncidentDraft` (`incident`, A) | `String title, String description, User reporter, ItService service, Category category, Category suggestedCategory, CategorySource categorySource, Impact impact, Urgency urgency, int affectedUsers` | title 1..200 chars; description 1..4000; reporter, service, category, categorySource, impact, urgency non-null; `affectedUsers ≥ 1`; `category.isActive()`; `suggestedCategory` nullable; **if `categorySource != MANUAL` then `suggestedCategory != null` and its id equals `category`'s id** |
| `UserDraft` (`factory`, A) | `String id, String name, String email, Role role, String department, Map<String,Integer> skillLevels, int yearsOfExperience, int maxActiveIncidents` | `id,name,email,role` non-null; role-specific fields checked by `UserFactory` (§N) |
| `PriorityInput` (`priority`, A) | `Impact impact, Urgency urgency, BusinessCriticality businessCriticality, int affectedUsers` | non-null enums; `affectedUsers ≥ 1` |
| `PriorityResult` (`priority`, A) | `int score, PriorityLevel level` | `score ≥ 1`; level non-null |
| `TechnicianScore` (`assignment`, A) | `Technician technician, double score, String reason` | technician non-null; score not NaN; reason non-blank |
| `CategorySuggestion` (`classification`, A) | `String categoryId, double confidence, String reason, CategorySource source` | id non-blank; `0.0 ≤ confidence ≤ 1.0`; reason non-blank; `source != MANUAL` |
| `SlaPolicy` (`model`, A) | `String serviceId, PriorityLevel priority, Duration responseTarget, Duration resolutionTarget` | `serviceId` nullable (**null = default policy**); priority non-null; targets positive; `responseTarget ≤ resolutionTarget`. Method: `boolean isDefault()` |
| `IncidentSearchCriteria` (`repository`, A) | `String incidentId, Instant createdFrom, Instant createdTo, IncidentStatus status, PriorityLevel priority, String categoryId, String serviceId, String technicianId` | all nullable (null = no filter); if both dates set then `createdFrom ≤ createdTo`. Static `IncidentSearchCriteria none()` |
| `SlaDeadlines` (`sla`, B) | `Instant responseDeadline, Instant resolutionDeadline` | non-null |
| `SlaEvaluation` (`sla`, B) | `SlaStatus response, SlaStatus resolution, SlaStatus overall, Instant effectiveResponseDeadline, Instant effectiveResolutionDeadline` | non-null |

### G.2 Application commands (`com.iitsm.application.command`, B) — IDs only
| Command | Fields | Used by |
|---|---|---|
| `CreateIncidentCommand` | `String actorId, String title, String description, String serviceId, String categoryId, String suggestedCategoryId /*nullable*/, CategorySource categorySource, Impact impact, Urgency urgency, int affectedUsers` | `createIncident` |
| `AssignIncidentCommand` | `String actorId, String incidentId, String technicianId` | `assignIncident`, `reassignIncident` |
| `UpdatePriorityInputsCommand` | `String actorId, String incidentId, Impact impact, Urgency urgency` | `updatePriorityInputs` |
| `IncidentTextCommand` | `String actorId, String incidentId, String text` | `addInvestigationNote` (note), `requestInfo` (question), `respondToInfoRequest` (response), `addAdditionalInfo` (info), `resolveIncident` (resolution summary) |
| `CloseIncidentCommand` | `String actorId, String incidentId, String closureNote /*nullable*/` | `closeIncident` |
| `SubmitFeedbackCommand` | `String actorId, String incidentId, int rating, String comment /*nullable*/` | `submitFeedback` |

Commands only reject `null`/blank ids with `InvalidInputException`; content rules live in the domain.

---

## H. Exceptions

Package `com.iitsm.domain.exception`. All unchecked. Base:
```java
public class ItsmException extends RuntimeException {
    private final String code;
    protected ItsmException(String code, String message)
    public String getCode()
}
```
Every subclass is `public class X extends ItsmException` with a single `public X(String message)` constructor, **except** `InvalidStateTransitionException`.

| Class | `getCode()` | Spec case | Thrown when |
|---|---|---|---|
| `InvalidInputException` | `INVALID_INPUT` | Invalid user input | null/blank/out-of-range arguments, constructor invariants, draft rules |
| `UnauthorizedOperationException` | `UNAUTHORIZED_OPERATION` | Unauthorized operation | permission denied; actor inactive |
| `InvalidIncidentException` | `INVALID_INCIDENT` | Invalid incident | guard violations that are not transitions: note/feedback/priority change in a status that forbids it, second feedback |
| `IncidentNotFoundException` | `INCIDENT_NOT_FOUND` | Invalid incident | no incident with that id |
| `InvalidStateTransitionException` | `INVALID_STATE_TRANSITION` | Invalid state transition | any illegal transition. Ctor: `(IncidentStatus currentStatus, String action)`; getters `getCurrentStatus()`, `getAction()`; message `"Cannot <action> an incident that is <STATUS>"` |
| `TechnicianUnavailableException` | `TECHNICIAN_UNAVAILABLE` | Technician unavailable | target technician inactive or `available == false` |
| `TechnicianOverloadedException` | `TECHNICIAN_OVERLOADED` | Workload exceeded | active incidents ≥ `maxActiveIncidents` |
| `ServiceNotFoundException` | `SERVICE_NOT_FOUND` | Service not found | unknown service id |
| `UserNotFoundException` | `USER_NOT_FOUND` | — | unknown user id |
| `CategoryNotFoundException` | `CATEGORY_NOT_FOUND` | — | unknown category id |
| `MissingSlaException` | `MISSING_SLA` | Missing SLA configuration | no override and no default policy; or an incident without deadlines is evaluated |
| `DuplicateIncidentException` | `DUPLICATE_INCIDENT` | Duplicate incident | **defined, not thrown in Phase 1** (Phase 3 duplicate detection) |

Not in Phase 1 (Phase 2): invalid login, database failure, network failure.

---

## I. Design patterns

| Pattern | Where | Participants | Why needed | Interaction |
|---|---|---|---|---|
| **State** | Incident lifecycle | `Incident` (context), `IncidentState`, `AbstractIncidentState`, 9 concrete states, `IncidentStates` | Spec/rubric require preventing illegal transitions as an OOP showcase; 9 states × 11 actions would otherwise be a nested `switch` | `Incident.assign(...)` calls `state.assign()`; the state returns the next state or throws; `Incident` then applies data changes and swaps `state`. |
| **Strategy** | Priority; technician assignment; category classification | `PriorityStrategy`/`WeightedPriorityStrategy`; `AssignmentStrategy`/`TechnicianScore`; `CategoryClassifier`/`NoOpClassifier` | Spec: "algorithm may change", "assignment strategy changed without redesign" | Callers (`IncidentFactory`, `IncidentService`) depend only on the interface; implementation injected through the constructor. |
| **Observer** | Domain events | `DomainEventPublisher` (subject), `DomainEventListener` (observer), `InMemoryDomainEventPublisher`, 12 event records | Audit, notifications, dashboard must react to changes without `IncidentService` knowing them | `IncidentService` publishes after save; the publisher calls each subscribed listener in subscription order. |
| **Repository** | Persistence | 5 repository interfaces, 5 in-memory implementations | Spec: persistence; Phase 2 swaps to JPA behind the same interfaces | Services use only interfaces. |
| **Factory** | Object creation | `IncidentFactory` (+`IncidentIdGenerator`, `PriorityStrategy`), `UserFactory` (+`UserDraft`) | Incident creation needs id + priority assembly; user creation returns the right subclass for a `Role` (polymorphism) | `IncidentService` → `IncidentFactory.create(draft, now)`; admin services (Phase 2) → `UserFactory.create(userDraft)`. |

Deliberately **not** used in Phase 1: Chain of Responsibility (escalation, Phase 3), Adapter (external channels, Phase 2/3), Decorator/Template Method (report generator, Phase 3), Builder.

---

## J. Incident state machine

### J.1 Valid transitions (complete)
| Current state | Action (`IncidentState` method) | Next state | Triggered by (service op) |
|---|---|---|---|
| NEW | `classify` | CLASSIFIED | `createIncident` (automatic) |
| CLASSIFIED | `assign` | ASSIGNED | `assignIncident`, `autoAssignIncident`, create-time auto-assign |
| ASSIGNED | `acknowledge` | ACKNOWLEDGED | `acknowledgeIncident` |
| ASSIGNED | `unassign` | CLASSIFIED | `reassignIncident` |
| ACKNOWLEDGED | `startWork` | IN_PROGRESS | `startWork` |
| ACKNOWLEDGED | `unassign` | CLASSIFIED | `reassignIncident` |
| IN_PROGRESS | `requestInfo` | WAITING_FOR_USER | `requestInfo` |
| IN_PROGRESS | `resolve` | RESOLVED | `resolveIncident` |
| WAITING_FOR_USER | `receiveInfo` | IN_PROGRESS | `respondToInfoRequest` |
| RESOLVED | `verify` | VERIFIED | `verifyResolution` |
| RESOLVED | `rejectResolution` | IN_PROGRESS | `rejectResolution` (reopen) |
| VERIFIED | `close` | CLOSED | `closeIncident` |

**Every other (state, action) pair is invalid** and throws `InvalidStateTransitionException(currentStatus, actionName)`. This includes `NEW → close` (the spec's example), `CLASSIFIED → resolve`, `RESOLVED → close` (must be verified first), and **every action on `CLOSED`** (terminal). 9 states × 11 actions = 99 pairs; 12 valid, 87 invalid. The B state test must enumerate all 99.

Action names (strings in the exception): `classify, assign, unassign, acknowledge, startWork, requestInfo, receiveInfo, resolve, rejectResolution, verify, close`.

### J.2 Guard predicates (per state)
| State | allowsInvestigationNotes | allowsAdditionalInfo | allowsFeedback | allowsPriorityChange |
|---|---|---|---|---|
| NEW | ✗ | ✓ | ✗ | ✓ |
| CLASSIFIED | ✗ | ✓ | ✗ | ✓ |
| ASSIGNED | ✗ | ✓ | ✗ | ✓ |
| ACKNOWLEDGED | ✓ | ✓ | ✗ | ✓ |
| IN_PROGRESS | ✓ | ✓ | ✗ | ✓ |
| WAITING_FOR_USER | ✓ | ✓ | ✗ | ✓ |
| RESOLVED | ✗ | ✗ | ✓ | ✗ |
| VERIFIED | ✗ | ✗ | ✓ | ✗ |
| CLOSED | ✗ | ✗ | ✓ | ✗ |

### J.3 Waiting / reopen / verification behaviour
* **Waiting:** `requestInfo` (technician) → `WAITING_FOR_USER`; the employee's `receiveInfo` returns to `IN_PROGRESS`. The resolution SLA clock is paused for the duration (§L.4).
* **Verification:** only the reporter verifies (`RESOLVED → VERIFIED`); only an administrator closes (`VERIFIED → CLOSED`).
* **Reopen:** the reporter may reject a resolution (`RESOLVED → IN_PROGRESS`); `resolution` is cleared and the resolution SLA is evaluated again against `now`.
* **Reassign:** `ASSIGNED`/`ACKNOWLEDGED → CLASSIFIED → ASSIGNED`. Not possible from `IN_PROGRESS` onward.
* No `CANCELLED` state.

---

## K. Priority

**Inputs** (`PriorityInput`): `Impact` (1–4, reporter), `Urgency` (1–4, reporter), `BusinessCriticality` (1–4, from `ItService`), `affectedUsers` ≥ 1 (reporter).

**Calculation** (`WeightedPriorityStrategy`, all constants `private static final` with names):
```
base  = impact.weight() × urgency.weight() × businessCriticality.weight()      // 1 … 64
bump  = affectedUsers ≤ 9 → 0 ; 10..49 → 4 ; 50..199 → 8 ; ≥ 200 → 12
score = base + bump                                                          // 1 … 76
level = score ≥ 36 → P1 ; score ≥ 18 → P2 ; score ≥ 8 → P3 ; else P4
```
**Outputs:** `PriorityResult(score, level)`. **Boundaries:** thresholds are inclusive (`≥`). Base products possible: 1,2,3,4,6,8,9,12,16,18,24,27,32,36,48,64 — so boundary tests use the bump to reach 35/17/7.

**Required oracle table (tests must assert exactly):**
| Impact | Urgency | Criticality | Users | Score | Level |
|---|---|---|---|---|---|
| CRITICAL | CRITICAL | CRITICAL | 1 | 64 | P1 |
| CRITICAL | CRITICAL | CRITICAL | 200 | 76 | P1 |
| HIGH | HIGH | CRITICAL | 1 | 36 | P1 (boundary) |
| CRITICAL | CRITICAL | MEDIUM | 1 | 32 | P2 |
| CRITICAL | CRITICAL | MEDIUM | 10 | 36 | P1 (bump lifts) |
| HIGH | HIGH | HIGH | 50 | 35 | P2 |
| MEDIUM | HIGH | HIGH | 1 | 18 | P2 (boundary) |
| HIGH | HIGH | LOW | 50 | 17 | P3 (just below 18) |
| MEDIUM | MEDIUM | MEDIUM | 1 | 8 | P3 (boundary) |
| LOW | LOW | HIGH | 10 | 7 | P4 (just below 8) |
| LOW | LOW | LOW | 1 | 1 | P4 |

> **Correction (oracle row 7):** the inputs were previously mislabelled `MEDIUM / MEDIUM / HIGH` (= 2 × 2 × 3 = 12, P3). The intended inclusive P2 boundary row is `MEDIUM / HIGH / HIGH / 1` (= 2 × 3 × 3 = 18, P2). This was an arithmetic/input-label error; the formula, weights and thresholds are unchanged.

Bump band boundaries to test: users 9→0, 10→4, 49→4, 50→8, 199→8, 200→12; users 0 → `InvalidInputException`.

**Rationale (for the report):** the spec's multiplicative formula makes any low factor dampen the result (a trivial-impact issue on a critical service cannot become P1); affected-user count adds breadth without letting a huge user count outweigh a trivial problem; thresholds put P1 at "three high factors" (3×3×4), P2 at "medium-high" (≥18), P3 at "all medium" (8). **Extensibility:** another algorithm = another `PriorityStrategy` bean; nothing else changes. Recalculation on input change: §Q (`updatePriorityInputs`).

---

## L. SLA

### L.1 Policy
`SlaPolicy(serviceId|null, priority, responseTarget, resolutionTarget)`. Default policies (serviceId = null), from the spec:

| Priority | Response | Resolution |
|---|---|---|
| P1 | 5 min | 30 min |
| P2 | 15 min | 2 h |
| P3 | 1 h | 8 h |
| P4 | 4 h | 24 h |

A per-service override is a policy with that `serviceId`. Lookup (`SlaPolicyResolver.resolve(serviceId, priority)`): override → default → else `MissingSlaException("No SLA policy for service <id> and priority <P>")`.

### L.2 Classes (B, `com.iitsm.domain.sla`)
```java
public final class SlaPolicyResolver { public SlaPolicyResolver(SlaPolicyRepository policies);
                                       public SlaPolicy resolve(String serviceId, PriorityLevel priority); }
public final class SlaCalculator    { public SlaCalculator(SlaPolicyResolver resolver);
                                       public SlaDeadlines calculate(String serviceId, PriorityLevel priority, Instant createdAt); }
public final class SlaEvaluator     { public SlaEvaluation evaluate(Incident incident, Instant now); }   // no-arg ctor, pure
```
### L.3 Deadline calculation
`responseDeadline = createdAt + responseTarget`; `resolutionDeadline = createdAt + resolutionTarget`. Example P1 created 09:00:00Z → response 09:05:00Z, resolution 09:30:00Z.

### L.4 Breach evaluation (`SlaEvaluator.evaluate`)
Precondition: both deadlines present, else `MissingSlaException("Incident <id> has no SLA deadlines")`.
```
effectiveResponseDeadline   = responseDeadline
effectiveResolutionDeadline = resolutionDeadline + pausedDuration
                              + (waitingSince present ? max(0, between(waitingSince, now)) : 0)
responseTarget   = between(createdAt, responseDeadline)       // base targets (no pauses)
resolutionTarget = between(createdAt, resolutionDeadline)
```
Per target (`doneAt` = `acknowledgedAt` for response; `resolvedAt` for resolution):
* `doneAt` present → `MET` if `!doneAt.isAfter(effectiveDeadline)`, else `BREACHED`.
* else `BREACHED` if `now.isAfter(effectiveDeadline)`;
  else `WARNING` if `!now.isBefore(effectiveDeadline − target/5)` (≤ 20 % of the target remains);
  else `ON_TRACK`.

`overall` = `BREACHED` if either is `BREACHED`; else `WARNING` if either is `WARNING`; else `MET` if both `MET`; else `ON_TRACK`.

Consequences the tests must pin: exactly at deadline → not breached (`WARNING`/`MET`); deadline + 1 ms → `BREACHED`; while `WAITING_FOR_USER` the effective resolution deadline moves with `now` (cannot newly breach); after `receiveInfo` the pause is folded into `pausedDuration`; after `rejectResolution` the resolution target is unmet again; acknowledgement at exactly the response deadline is `MET`.

**Missing configuration:** `createIncident` fails with `MissingSlaException` **before anything is saved or published**.
**Time/testing:** no clocks inside SLA classes; `now` is a parameter; tests use `TestClock`. No scheduler in Phase 1 (Phase 3's monitor will call `SlaEvaluator.evaluate` and publish `SlaWarning`/`SlaViolated`).

---

## M. Repositories

Package `com.iitsm.domain.repository` (interfaces, A). Rules for every repository: `save` is an **upsert keyed by id**, returns the saved object; `findById` returns `Optional` (the **service** turns empty into the right `*NotFoundException`); list methods return a new unmodifiable list in insertion order; null arguments → `InvalidInputException`.

```java
public interface IncidentRepository {
    Incident save(Incident incident);
    Optional<Incident> findById(String id);
    List<Incident> findAll();                                  // insertion order
    List<Incident> findByReporterId(String reporterId);
    List<Incident> findByAssignedTechnicianId(String technicianId);
    int countActiveByTechnicianId(String technicianId);        // assigned technician matches && status.countsTowardWorkload()
    List<Incident> search(IncidentSearchCriteria criteria);    // AND of non-null criteria
}
public interface UserRepository {
    User save(User user);   Optional<User> findById(String id);
    List<User> findAll();   List<Technician> findAllTechnicians();
}
public interface ItServiceRepository { ItService save(ItService s); Optional<ItService> findById(String id); List<ItService> findAll(); }
public interface CategoryRepository  { Category save(Category c); Optional<Category> findById(String id);
                                       List<Category> findAll(); List<Category> findAllActive(); }
public interface SlaPolicyRepository { SlaPolicy save(SlaPolicy p);                       // replaces same (serviceId, priority)
                                       Optional<SlaPolicy> findOverride(String serviceId, PriorityLevel priority);
                                       Optional<SlaPolicy> findDefault(PriorityLevel priority);
                                       List<SlaPolicy> findAll(); }
```
`search` semantics: `incidentId` exact match; `createdFrom`/`createdTo` inclusive bounds on `createdAt`; `status`, `priority` equality; `categoryId` = incident category id; `serviceId` = incident service id; `technicianId` = assigned technician id (incident without assignee never matches a non-null technicianId). `IncidentSearchCriteria.none()` returns all.

**In-memory implementations** (`infrastructure.persistence.memory`, A): each wraps a `LinkedHashMap` (SLA policies keyed by `serviceId + "|" + priority`, default key `"*|" + priority`); **all public methods `synchronized`**; same-reference storage (assumption A.4.4). `InMemoryIncidentIdGenerator implements IncidentIdGenerator`: `AtomicLong` starting at 1001, returns `"INC" + n`; constructor `InMemoryIncidentIdGenerator()` and `InMemoryIncidentIdGenerator(long firstNumber)`.

---

## N. Factories

### N.1 `IncidentFactory` (`domain.factory`, A)
```java
public IncidentFactory(IncidentIdGenerator ids, PriorityStrategy priorityStrategy)
public Incident create(IncidentDraft draft, Instant createdAt)
```
Steps: `id = ids.nextIncidentId()`; `PriorityResult r = priorityStrategy.calculate(new PriorityInput(draft.impact(), draft.urgency(), draft.service().getBusinessCriticality(), draft.affectedUsers()))`; `return new Incident(id, draft, r, createdAt)`. Returns status `NEW`, no deadlines (SLA is the service's job, Q-N2). Null args → `InvalidInputException`.

### N.2 `UserFactory` (`domain.factory`, A)
```java
public User create(UserDraft draft)
```
| `draft.role()` | Returns | Required fields |
|---|---|---|
| `EMPLOYEE` | `Employee` | `department` |
| `TECHNICIAN`, `TEAM_LEAD` | `Technician` | `skillLevels` (non-null), `yearsOfExperience`, `maxActiveIncidents` |
| `ADMINISTRATOR`, `IT_MANAGER` | `Administrator` | — |

Missing required fields → `InvalidInputException`. No passwords/credentials (Phase 2, separate security class). `NotificationFactory`: Phase 2.

---

## O. Classification

* Interface `CategoryClassifier` (§F.4), result `CategorySuggestion` (§G.1), default `NoOpClassifier` (`public final class`, always `Optional.empty()`).
* `Incident` carries `suggestedCategory` (nullable) and `categorySource` (`MANUAL|KEYWORD|LLM`); the final `category` is always supplied by the reporter (Q-N10a).
* Semantics of `categorySource` = **how the final category was chosen**: `MANUAL` = reporter picked it (a suggestion may or may not exist; if it exists and differs, that is an *override*); `KEYWORD`/`LLM` = reporter accepted that classifier's suggestion (then `suggestedCategory` equals `category`).
* Integration: `IncidentService.suggestCategory(actorId, title, description)` calls the injected classifier and **discards** a suggestion whose `categoryId` is unknown or inactive (returns empty). `createIncident` never calls the classifier.
* Endpoint contract `POST /api/incidents/suggest-category` is documented in `docs/api-contract.md`; not built.

---

## P. Domain events

### P.1 Contract
```java
public interface DomainEvent {            // not sealed (R1)
    Instant occurredAt();  String actorId();  String incidentId();
    IncidentStatus previousStatus();        // null only for IncidentCreated
    IncidentStatus newStatus();
    String action();                        // stable upper-snake identifier
    String description();                   // human readable, non-blank, NOT asserted verbatim by tests
}
public interface DomainEventListener { void onEvent(DomainEvent event);
                                       default boolean critical() { return false; } }
public interface DomainEventPublisher { void publish(DomainEvent event);  void subscribe(DomainEventListener listener); }
```
These cover the spec's 7 audit fields: timestamp, user (`actorId`), action, incident ID, previous state, new state, description.

Every event record starts with the common components `(Instant occurredAt, String actorId, String incidentId, IncidentStatus previousStatus, IncidentStatus newStatus, String description)` followed by the extras below. Fixed-action events declare `public String action() { return "<CONSTANT>"; }`. Compact constructors reject null `occurredAt/actorId/incidentId/newStatus` and blank `description`. The system actor id constant is `"SYSTEM"`.

| Record | Extra components | `action()` | Status change |
|---|---|---|---|
| `IncidentCreated` | `String reporterId, String serviceId, String categoryId, String suggestedCategoryId, CategorySource categorySource, PriorityLevel priority, int priorityScore` | `INCIDENT_CREATED` | null → NEW |
| `IncidentClassified` | `String categoryId` | `INCIDENT_CLASSIFIED` | NEW → CLASSIFIED |
| `PriorityChanged` | `PriorityLevel previousPriority, PriorityLevel newPriority, int previousScore, int newScore` | `PRIORITY_CHANGED` | unchanged (prev = new) |
| `IncidentAssigned` | `String technicianId, boolean automatic` | `INCIDENT_ASSIGNED` | CLASSIFIED → ASSIGNED |
| `IncidentAcknowledged` | `String technicianId` | `INCIDENT_ACKNOWLEDGED` | ASSIGNED → ACKNOWLEDGED |
| `IncidentStatusChanged` | `String action` | component: `UNASSIGNED`, `WORK_STARTED`, `INFO_REQUESTED`, `INFO_RECEIVED`, `RESOLUTION_REJECTED`, `VERIFIED` | per §J |
| `IncidentResolved` | `String resolverId` | `INCIDENT_RESOLVED` | IN_PROGRESS → RESOLVED |
| `IncidentClosed` | `String closedById` | `INCIDENT_CLOSED` | VERIFIED → CLOSED |
| `IncidentUpdated` | `String action` | component: `NOTE_ADDED`, `ADDITIONAL_INFO_ADDED`, `FEEDBACK_SUBMITTED` | unchanged |
| `AssignmentFailed` | — | `ASSIGNMENT_FAILED` | CLASSIFIED → CLASSIFIED |
| `SlaWarning` | `SlaType slaType, Instant deadline` | `SLA_WARNING` | unchanged — **defined now, published in Phase 3** |
| `SlaViolated` | `SlaType slaType, Instant deadline` | `SLA_VIOLATED` | unchanged — **defined now, published in Phase 3** |

### P.2 Publishing points
`IncidentService` publishes **after `repository.save`**, in this order, all stamped with the single `now` of that operation (§Q.3 table). Reads and `suggestCategory` publish nothing.

### P.3 `InMemoryDomainEventPublisher` (`infrastructure.event`, B)
Synchronous; listeners kept in subscription order (`CopyOnWriteArrayList`). `publish`: calls each listener; if a listener throws and `critical() == false` → caught and logged with `System.Logger` (WARNING) and delivery continues; if `critical() == true` → exception propagates immediately. Null event/listener → `InvalidInputException`.

---

## Q. Incident service (B) — `com.iitsm.application.service`

### Q.1 Class contract
```java
public final class IncidentService {
    public static final String SYSTEM_ACTOR_ID = "SYSTEM";
    public IncidentService(IncidentRepository incidents, UserRepository users, ItServiceRepository services,
                           CategoryRepository categories, IncidentFactory incidentFactory,
                           PriorityStrategy priorityStrategy, SlaCalculator slaCalculator,
                           AssignmentStrategy assignmentStrategy, CategoryClassifier classifier,
                           IncidentPermissionPolicy permissions, DomainEventPublisher events, Clock clock)
    // operations in Q.3 — all public, all return the affected Incident unless stated
}
```
Plain class, **no Spring annotations**. Reads `clock.instant()` **once per operation**. Constructor rejects null arguments (`InvalidInputException`).

`IncidentOperation` (enum, B): `CREATE, VIEW, ASSIGN, ACKNOWLEDGE, START_WORK, ADD_INVESTIGATION_NOTE, REQUEST_INFO, RESPOND_INFO, ADD_ADDITIONAL_INFO, RESOLVE, VERIFY, REJECT_RESOLUTION, CLOSE, SUBMIT_FEEDBACK, UPDATE_PRIORITY, SUGGEST_CATEGORY`.
`IncidentPermissionPolicy` (B): `public IncidentPermissionPolicy()`; `public void check(User actor, IncidentOperation operation, Incident incident)` — `incident` is null for `CREATE` and `SUGGEST_CATEGORY`; throws `UnauthorizedOperationException`.

### Q.2 Role matrix (frozen)
Role groups: **Employee** = `EMPLOYEE`; **Technician** = `TECHNICIAN`, `TEAM_LEAD`; **Admin** = `ADMINISTRATOR`, `IT_MANAGER`.

| Operation | Employee | Technician | Admin |
|---|---|---|---|
| CREATE | ✓ | ✗ | ✗ |
| VIEW | only own reported | only assigned to them | any |
| ASSIGN (assign, reassign, auto-assign) | ✗ | ✗ | ✓ |
| ACKNOWLEDGE, START_WORK, ADD_INVESTIGATION_NOTE, REQUEST_INFO, RESOLVE | ✗ | only if assigned to them | ✗ |
| RESPOND_INFO, ADD_ADDITIONAL_INFO, VERIFY, REJECT_RESOLUTION, SUBMIT_FEEDBACK | only own reported | ✗ | ✗ |
| CLOSE | ✗ | ✗ | ✓ |
| UPDATE_PRIORITY | ✗ | only if assigned to them | ✓ |
| SUGGEST_CATEGORY | ✓ | ✓ | ✓ |

An **inactive** actor is always rejected (`UnauthorizedOperationException`).

### Q.3 Standard sequence and order of checks (applies to every operation unless stated)
1. Command/argument null or blank id → `InvalidInputException`.
2. Load actor: `UserNotFoundException`; inactive → `UnauthorizedOperationException`.
3. Load target(s): `IncidentNotFoundException` (and others as listed).
4. `permissions.check(...)` → `UnauthorizedOperationException`.
5. Domain call(s). The `Incident` validates content first (`InvalidInputException`), then state (`InvalidStateTransitionException`/`InvalidIncidentException`).
6. `incidents.save(incident)`.
7. Publish events (same `now`), then return the incident.

### Q.4 Operations
Legend — **Repo** = repository calls besides the standard load/save.

| # | Signature | Preconditions & domain call | State change | Events (in order) | Extra exceptions |
|---|---|---|---|---|---|
| 1 | `Incident createIncident(CreateIncidentCommand c)` | Actor is Employee (CREATE). Load service (`ServiceNotFoundException`), category and optional suggested category (`CategoryNotFoundException`). Build `IncidentDraft` (reporter = actor). `incidentFactory.create(draft, now)`. `slaCalculator.calculate(serviceId, priority, now)` → `incident.applySlaDeadlines(...)`. `incident.classify()`. Save. Then **auto-assign attempt** (Q.5). | null → NEW → CLASSIFIED (→ ASSIGNED if a technician is found) | `IncidentCreated`, `IncidentClassified`, then `IncidentAssigned(automatic=true, actor=SYSTEM)` **or** `AssignmentFailed` | `MissingSlaException` (nothing saved/published), `InvalidInputException` (draft rules) |
| 2 | `Incident assignIncident(AssignIncidentCommand c)` | ASSIGN. `incident.ensureAssignable()`. Load technician: not found → `UserNotFoundException`; not a `Technician` → `InvalidInputException`; `!isAssignable()` → `TechnicianUnavailableException`; `isOverloaded(incidents.countActiveByTechnicianId(id))` → `TechnicianOverloadedException`. `incident.assign(tech, now)`. | CLASSIFIED → ASSIGNED | `IncidentAssigned(automatic=false)` | `InvalidStateTransitionException` |
| 3 | `Incident reassignIncident(AssignIncidentCommand c)` | ASSIGN. `incident.ensureUnassignable()`. Same technician checks as #2; target equal to the current assignee → `InvalidInputException`. Then `unassign()`, `assign(tech, now)`. | ASSIGNED/ACKNOWLEDGED → CLASSIFIED → ASSIGNED | `IncidentStatusChanged(UNASSIGNED)`, `IncidentAssigned(automatic=false)` | `InvalidStateTransitionException` |
| 4 | `Incident autoAssignIncident(String actorId, String incidentId)` | ASSIGN. `incident.ensureAssignable()`. Runs the auto-assign routine (Q.5) with actor = `actorId`. | CLASSIFIED → ASSIGNED, or unchanged | `IncidentAssigned(automatic=true)` or `AssignmentFailed` | `InvalidStateTransitionException` |
| 5 | `Incident acknowledgeIncident(String actorId, String incidentId)` | ACKNOWLEDGE. `incident.acknowledge(now)`. | ASSIGNED → ACKNOWLEDGED | `IncidentAcknowledged` | `InvalidStateTransitionException` |
| 6 | `Incident startWork(String actorId, String incidentId)` | START_WORK. `incident.startWork()`. | ACKNOWLEDGED → IN_PROGRESS | `IncidentStatusChanged(WORK_STARTED)` | same |
| 7 | `Incident addInvestigationNote(IncidentTextCommand c)` | ADD_INVESTIGATION_NOTE. `incident.addInvestigationNote(actorId, text, now)`. | none | `IncidentUpdated(NOTE_ADDED)` | `InvalidIncidentException` |
| 8 | `Incident requestInfo(IncidentTextCommand c)` | REQUEST_INFO. `incident.requestInfo(actorId, text, now)`. | IN_PROGRESS → WAITING_FOR_USER | `IncidentStatusChanged(INFO_REQUESTED)` | `InvalidStateTransitionException` |
| 9 | `Incident respondToInfoRequest(IncidentTextCommand c)` | RESPOND_INFO. `incident.receiveInfo(actorId, text, now)`. | WAITING_FOR_USER → IN_PROGRESS | `IncidentStatusChanged(INFO_RECEIVED)` | same |
| 10 | `Incident addAdditionalInfo(IncidentTextCommand c)` | ADD_ADDITIONAL_INFO. `incident.addAdditionalInfo(...)`. | none | `IncidentUpdated(ADDITIONAL_INFO_ADDED)` | `InvalidIncidentException` |
| 11 | `Incident resolveIncident(IncidentTextCommand c)` | RESOLVE. `incident.resolve(actorId, text, now)`. | IN_PROGRESS → RESOLVED | `IncidentResolved` | `InvalidStateTransitionException` |
| 12 | `Incident verifyResolution(String actorId, String incidentId)` | VERIFY. `incident.verify(now)`. | RESOLVED → VERIFIED | `IncidentStatusChanged(VERIFIED)` | same |
| 13 | `Incident rejectResolution(String actorId, String incidentId)` | REJECT_RESOLUTION. `incident.rejectResolution()`. | RESOLVED → IN_PROGRESS | `IncidentStatusChanged(RESOLUTION_REJECTED)` | same |
| 14 | `Incident closeIncident(CloseIncidentCommand c)` | CLOSE. `incident.close(actorId, closureNote, now)`. | VERIFIED → CLOSED | `IncidentClosed` | same |
| 15 | `Incident submitFeedback(SubmitFeedbackCommand c)` | SUBMIT_FEEDBACK. `incident.submitFeedback(actorId, rating, comment, now)`. | none | `IncidentUpdated(FEEDBACK_SUBMITTED)` | `InvalidIncidentException` |
| 16 | `Incident updatePriorityInputs(UpdatePriorityInputsCommand c)` | UPDATE_PRIORITY. If impact **and** urgency equal the current ones → return unchanged (no save, no event). Else compute `PriorityResult` with `service.getBusinessCriticality()` and `affectedUsers`; compute new deadlines via `slaCalculator` from `createdAt` (**if the incident was already acknowledged the existing response deadline is kept**); then `incident.updatePriority(...)` and `incident.applySlaDeadlines(...)`. | none | `PriorityChanged` | `MissingSlaException` (before any mutation), `InvalidIncidentException` |
| 17 | `Incident getIncident(String actorId, String incidentId)` | VIEW. Read-only (no save). | none | none | — |
| 18 | `List<Incident> listVisibleIncidents(String actorId)` | Active actor. Employee → `findByReporterId`; Technician → `findByAssignedTechnicianId`; Admin → `findAll`. | none | none | — |
| 19 | `Optional<CategorySuggestion> suggestCategory(String actorId, String title, String description)` | SUGGEST_CATEGORY. `classifier.classify(...)`; discard if the category is unknown or inactive in `CategoryRepository`. | none | none | — |

### Q.5 Auto-assign routine (private, used by #1 and #4)
1. `technicians = users.findAllTechnicians()`; `workload[id] = incidents.countActiveByTechnicianId(id)` for each.
2. `candidates` = those with `isAssignable()` and `!isOverloaded(workload[id])`.
3. If `candidates` is empty → publish `AssignmentFailed` (description "No available technician"); stop (incident stays `CLASSIFIED`).
4. `pick = strategy.pick(incident, candidates, workload)`; empty → `AssignmentFailed("Strategy <name> returned no technician")`; stop.
5. `incident.assign(pick, now)`; save; publish `IncidentAssigned(automatic=true)`.
Actor id in the events: `SYSTEM` when called from `createIncident`; the admin's id when called from #4.

---

## R. Developer ownership

The mandated split is kept; **no deviation from the official spec is required**. Added items (assignment contract, factories' drafts, in-memory publisher, commands, permission policy) are assigned as follows.

| Developer A (project owner) | Developer B (teammate) |
|---|---|
| `domain.model` (all), `domain.exception` (all) | `domain.incident` state files (`IncidentState`, `AbstractIncidentState`, 9 states, `IncidentStates`) |
| `domain.incident`: `Incident`, `IncidentNote`, `Resolution`, `Feedback`, `IncidentDraft` | `domain.sla` (all) |
| `domain.priority` (all) | `domain.event` (all) |
| `domain.factory` (all) | `infrastructure.event.InMemoryDomainEventPublisher` |
| `domain.repository` (all) | `application.command` (all) |
| `domain.assignment` (interface + `TechnicianScore`) | `application.service` (all) |
| `domain.classification` (all) | B's tests (§T) and `docs/sla-and-lifecycle-design.md` |
| `infrastructure.persistence.memory` (all) | |
| `src/test/.../support/*`, A's tests, `docs/api-contract.md`, `docs/priority-design.md` | |
| Housekeeping: pom, `.gitignore`, `application.yaml`, secret removal | |

Cross-dependencies (the only ones): A's `Incident` uses B's `IncidentStates` (gate G1); B's SLA/service code uses A's model, `Incident`, repositories (gates G0, G2). The gate order in `IMPLEMENTATION_MANIFEST.md` removes any waiting beyond these.

---

## S. Shared files

### S.1 Cross-boundary contract files (frozen after the freeze commit)
Edited only by the owner **and only by agreement**; consumers are the other developer's code.

| Group | Files | Owner | Created first by | Consumed by |
|---|---|---|---|---|
| Enums | the 8 enums in `domain.model` | A | A (gate G0) | B |
| Exceptions | all 13 in `domain.exception` | A | A (gate G0) | B |
| Model | `User`, `Employee`, `Technician`, `Administrator`, `ItService`, `Category`, `SlaPolicy` | A | A (gate G1A) | B |
| Incident aggregate | `Incident`, `IncidentDraft`, `IncidentNote`, `Resolution`, `Feedback` | A | A (gate G2) | B |
| Interfaces/records | `PriorityStrategy`, `PriorityInput`, `PriorityResult`, `AssignmentStrategy`, `TechnicianScore`, `CategoryClassifier`, `CategorySuggestion`, all repository interfaces + `IncidentSearchCriteria`, `IncidentIdGenerator`, `IncidentFactory`, `UserFactory`, `UserDraft` | A | A (gate G1A) | B |
| State | `IncidentState`, `IncidentStates` | B | B (gate G1) | A |
| Events | `DomainEvent`, `DomainEventPublisher`, `DomainEventListener`, 12 event records | B | B | A (Phase 2 listeners) |
| SLA | `SlaStatus`, `SlaType`, `SlaEvaluation` | B | B | A (Phase 2 search) |
| Test support | `TestClock`, `TestData` | A | A (gate G2) | B |

### S.2 Files either developer might touch (the real merge-conflict risks)
| File | Owner | Rule |
|---|---|---|
| `backend/pom.xml` | A | Changed only in housekeeping now; Phase 2 dependency additions need approval (AGENTS.md). |
| `.gitignore`, `backend/src/main/resources/application.yaml` | A | Housekeeping only. |
| `docs/api-contract.md` | A | B comments by PR/chat; edits only by A. |
| `docs/CONTRACT_FREEZE*.md`, `docs/IMPLEMENTATION_MANIFEST.md` | both | Frozen; amended only by a joint "contract amendment" commit. |
| `AGENTS.md`, `README.md` | both | Not edited in Phase 1 except A's one-line status update. |
| `docs/ai-log-A.md`, `docs/ai-log-B.md` | A / B | Separate files so there are never conflicts. |

Workflow: `git pull --rebase origin main` before each push; push small commits; never edit a file owned by the other developer — ask them (or request an amendment).
**Amendment rule:** adding a *new* file or a *new* public method/class to your own non-frozen file is free; changing or removing anything listed in §W requires both developers' agreement recorded in a commit message `contract-amendment: …`.

---

## T. Testing

JUnit 5 + Mockito only (no new dependency). **No Spring context.** Test method naming: `method_condition_expectedResult`. Time via `TestClock` only. Tests are written **from this contract**, not from the implementation (Plan rule 7). Delete `IitsmApplicationTests` in housekeeping. A parameterized test counts as one method.

### T.1 Test support (A, `src/test/java/com/iitsm/support/`)
```java
public final class TestClock extends Clock {
    public static final Instant DEFAULT_START = Instant.parse("2026-11-16T09:00:00Z");
    public TestClock();  public TestClock(Instant start);
    public void advance(Duration d);  public void set(Instant t);
    public Instant instant();  public ZoneId getZone();  /* UTC */  public Clock withZone(ZoneId z);
}
public final class TestData {
    public static Employee employee(String id);                         // department "Finance"
    public static Technician technician(String id, int maxActive);      // TECHNICIAN, skills {NETWORK:3, DATABASE:2}, 5 yrs
    public static Technician teamLead(String id);                       // TEAM_LEAD
    public static Administrator admin(String id);                       // ADMINISTRATOR
    public static ItService service(String id, BusinessCriticality c);
    public static Category category(String id);                         // name = id
    public static List<Category> defaultCategories();                   // the 9 spec categories
    public static List<SlaPolicy> defaultSlaPolicies();                 // the 4 spec rows, serviceId = null
    public static IncidentDraft draft(User reporter, ItService service, Category category,
                                      Impact impact, Urgency urgency, int affectedUsers);   // MANUAL, no suggestion
}
```
B-only test helpers (`StubAssignmentStrategy`, `ServiceFixture`, `RecordingListener`) live in B's own test files.

### T.2 Required test classes and **minimum** test methods
**A (total ≥ 62)**
| Test class | Min | Required scenarios |
|---|---|---|
| `UserHierarchyTest` | 6 | blank id/name; invalid email; subclass/role mismatch (Employee never gets TECHNICIAN; Technician accepts TECHNICIAN & TEAM_LEAD only); skill map unmodifiable; `isAssignable`/`isOverloaded` boundaries; deactivate |
| `CatalogEntitiesTest` | 4 | `ItService`, `Category` id/name rules and equality; `SlaPolicy` validation (targets > 0, response ≤ resolution, `isDefault`) |
| `WeightedPriorityStrategyTest` | 10 | the whole §K oracle table (parameterized), all band boundaries, `affectedUsers = 0` rejected, null input |
| `IncidentFactoryTest` | 4 | id from generator, priority applied, status `NEW`, `createdAt`, null args |
| `UserFactoryTest` | 5 | one per role group → correct subclass; missing department/skills → `InvalidInputException` |
| `IncidentTest` | 14 | constructor invariants; draft rules incl. category-source/suggestion consistency; `applySlaDeadlines` validation; notes allowed/forbidden by state; feedback once only + rating range; `requestInfo`/`receiveInfo` pause accounting; first-acknowledge retained after unassign; resolve/reject clears resolution; `updatePriority` guard; failed call leaves state unchanged; unmodifiable getters |
| `InMemoryIncidentRepositoryTest` | 10 | save/find/upsert; insertion order; `findByReporterId`; `findByAssignedTechnicianId`; `countActiveByTechnicianId` counts only workload statuses; `search` each of the 7 criteria; combined AND; `none()`; null args |
| `InMemoryReferenceRepositoriesTest` | 8 | User/ItService/Category/SlaPolicy repos (override vs default lookup, replace same key, `findAllTechnicians`, `findAllActive`); id generator format `INC1001`, increment, custom start |
| `NoOpClassifierTest` | 1 | always empty, incl. blank input |

**B (total ≥ 76)**
| Test class | Min | Required scenarios |
|---|---|---|
| `IncidentStateTransitionTest` | 3 | **all 99 (state × action) pairs** (12 valid → expected next state; 87 invalid → `InvalidStateTransitionException` with correct status/action); guard-predicate table §J.2; `IncidentStates.of` is total and `status()` round-trips |
| `SlaPolicyResolverTest` | 3 | override wins; default fallback; missing → `MissingSlaException` |
| `SlaCalculatorTest` | 3 | four spec rows (parameterized); per-service override; missing config |
| `SlaEvaluatorTest` | 12 | on track; warning at exactly `deadline − target/5` and not 1 ms before; exactly at deadline not breached; +1 ms breached; response met at acknowledge (on time / late); resolution met; pause does not breach; pause folded after `receiveInfo`; reject-resolution un-meets; `overall` precedence; incident without deadlines → `MissingSlaException` |
| `InMemoryDomainEventPublisherTest` | 5 | delivery order; all subscribers called; non-critical failure isolated; critical failure propagates; null event/listener |
| `IncidentPermissionPolicyTest` | 8 | the §Q.2 matrix per role (parameterized); ownership rules (reporter/assignee); inactive actor |
| `IncidentServiceCreateTest` | 9 | success: status, priority, deadlines, events in order; missing SLA → nothing saved/published; unknown service/category; non-employee rejected; inactive actor; auto-assign success (stub); no candidate → `AssignmentFailed`; suggested-category invariants |
| `IncidentServiceAssignmentTest` | 8 | manual assign; unavailable; overloaded; target not a technician; wrong state; non-admin rejected; reassign (events, same-technician rejected); `autoAssignIncident` |
| `IncidentServiceWorkflowTest` | 14 | each of #5–#15: happy path + wrong actor + wrong state; blank text; feedback twice |
| `IncidentServiceQueryAndPriorityTest` | 8 | `updatePriorityInputs` recalculates score+deadlines+event; unchanged inputs → no event; acknowledged keeps response deadline; forbidden after RESOLVED; `getIncident` visibility per role; `listVisibleIncidents` per role; not found; `suggestCategory` (no-op empty, valid kept, invalid discarded) |
| `IncidentLifecycleInMemoryTest` | 3 | **complete lifecycle** NEW→CLASSIFIED→ASSIGNED→ACKNOWLEDGED→IN_PROGRESS→RESOLVED→VERIFIED→CLOSED with in-memory repos + real publisher + `RecordingListener` asserting the exact event sequence and final SLA = `MET`/`MET`; waiting loop with pause; reject-resolution loop |

Sums: A = 6+4+10+4+5+14+10+8+1 = **62**; B = 3+3+3+12+5+8+9+8+14+8+3 = **76**; total **138** (spec minimum 15, plan target 30+). Every test category the spec lists is covered: normal (lifecycle), boundary (priority/SLA/bump), invalid input, exceptions (all §H except `DuplicateIncidentException`), authorization (matrix), business rules (pause, reopen, workload).

---

## U. Documentation

| Deliverable | Owner | When |
|---|---|---|
| `docs/CONTRACT_FREEZE.md`, `CONTRACT_FREEZE_SUMMARY.md`, `IMPLEMENTATION_MANIFEST.md` | both (frozen) | now |
| `docs/api-contract.md` — endpoint table (§U.1), role per endpoint, request/response JSON for each command/`IncidentResponse`, error format and exception→HTTP map | A | Phase 1 (before any controller) |
| `docs/priority-design.md` — formula, bands, thresholds, oracle table, rationale | A | Phase 1 |
| `docs/sla-and-lifecycle-design.md` — SLA rules, state table §J, guard table, pause rule | B | Phase 1 |
| `docs/ai-log-A.md`, `docs/ai-log-B.md` — what was asked of AI (Plan rule 6) | each | continuous |
| Class diagram, use-case diagram, architecture diagram, ER diagram, report | B (class/use-case), A (architecture/ER) per Plan §9 | after code settles (Phase 4) — generated from code and checked against it |
| Javadoc on public types of the domain: one sentence of responsibility | owners | Phase 1 |

### U.1 API endpoint list (documented now, **built in Phase 2**)
All under `/api`, JSON, JWT-authenticated (Phase 2) — the authenticated user id becomes `actorId`. Success: 200/201 with `IncidentResponse`.

| Method + path | Role | Service operation |
|---|---|---|
| `POST /incidents` | Employee | `createIncident` |
| `GET /incidents` | all (filtered by visibility) | `listVisibleIncidents` (+ search params in Phase 2) |
| `GET /incidents/{id}` | all (visibility) | `getIncident` |
| `POST /incidents/suggest-category` | all | `suggestCategory` |
| `POST /incidents/{id}/assign` · `/reassign` · `/auto-assign` | Admin | #2 #3 #4 |
| `POST /incidents/{id}/acknowledge` · `/start-work` · `/notes` · `/request-info` · `/resolve` | Technician (assigned) | #5 #6 #7 #8 #11 |
| `POST /incidents/{id}/respond-info` · `/additional-info` · `/verify` · `/reject-resolution` · `/feedback` | Employee (reporter) | #9 #10 #12 #13 #15 |
| `POST /incidents/{id}/close` | Admin | #14 |
| `PUT /incidents/{id}/priority-inputs` | Admin, Technician (assigned) | #16 |

Error JSON: `{"timestamp":"…","status":409,"code":"INVALID_STATE_TRANSITION","message":"…","details":[]}`.
HTTP map: `INVALID_INPUT`→400; `UNAUTHORIZED_OPERATION`→403; `*_NOT_FOUND`→404; `INVALID_STATE_TRANSITION`, `INVALID_INCIDENT`, `TECHNICIAN_UNAVAILABLE`, `TECHNICIAN_OVERLOADED`, `DUPLICATE_INCIDENT`→409; `MISSING_SLA`→500; (Phase 2) invalid login→401.
`IncidentResponse` fields: `id, title, description, reporterId, serviceId, categoryId, suggestedCategoryId, categorySource, impact, urgency, affectedUsers, priority, priorityScore, status, assignedTechnicianId, createdAt, responseDeadline, resolutionDeadline, acknowledgedAt, resolvedAt, verifiedAt, closedAt, notes[], resolution, feedback`.
Phase 2 endpoints (admin CRUD, audit, dashboard, search, login) are **not frozen** here.

---

## V. Implementation manifest

Same table as `IMPLEMENTATION_MANIFEST.md` (which additionally contains gates and AI-session briefs).

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

---

## W. Frozen contracts (change only with **both** developers' agreement)

1. **Package structure** — §C exactly; no new top-level packages in Phase 1; `api`/`config` appear only in Phase 2.
2. **Class and interface names** — every name in §C, §D–§G, §P, §Q.
3. **Method signatures** — every signature printed in §D.7, §F, §L.2, §M, §N, §Q.1, §Q.4 (names, parameter types **and order**, return types, `Optional` vs nullable).
4. **Enums** — names, values, **order**, weights, and the `countsTowardWorkload()` set (§E).
5. **Domain relationships** — `Incident` → `User`(reporter), `ItService`, `Category`, `Technician`; `Incident` composes notes/`Resolution`/`Feedback`; `User` → 3 subclasses; `Role` ↔ subclass rules (§D.1–D.4).
6. **State transitions** — the 12 valid transitions, the guard table, action names (§J).
7. **Repository contracts** — §M method list, upsert/`Optional`/ordering/null semantics, search semantics, same-reference storage.
8. **Event contracts** — the 12 records, component order, action constants, the `DomainEvent` methods, `DomainEventListener.critical()`, publish-after-save rule, event order per operation (§P, §Q.4).
9. **Service contracts** — the 19 operations, order of checks (§Q.3), role matrix (§Q.2), auto-assign routine (§Q.5), exception per case.
10. **Rules** — priority formula/bands/thresholds (§K), SLA rules (§L), error codes (§H), `IncidentStatus` workload set.
11. **Frozen docs** — these three markdown files.

**Allowed without agreement:** a new file; a new public method/class in your own non-frozen file; private/internal changes; tests beyond the minimums; wording in your own docs.

---

## 6. Final validation (consistency check performed before freezing)

### 6.1 Requirement → implementation
| Spec requirement | Phase 1 element | Result |
|---|---|---|
| 3 user types | `User` hierarchy, `UserFactory` | ✓ (admin CRUD Phase 2) |
| Incident create / view / update / assign / resolve / close | `IncidentService` #1–#19 | ✓ "update" = notes, additional info, priority inputs |
| Priority (impact, urgency, criticality, users, service) | §K; service supplies criticality | ✓ |
| ≥2 assignment strategies | contract + manual/auto path | ◐ implementations Phase 2 (planned, no contract change) |
| SLA calc / deadlines / violation detection | §L | ✓ detection pure; recording Phase 3 |
| Warnings before deadlines | `SlaStatus.WARNING` + `SlaWarning` event | ✓ evaluable; publishing Phase 3 |
| Lifecycle, no `NEW→CLOSED` | §J, 99-pair test | ✓ |
| Notifications ≥2 | events carry data | ◐ Phase 2 |
| Audit (7 fields) | `DomainEvent` | ◐ record in Phase 2 |
| Search ≥5 | 7 criteria | ✓ (+SLA status Phase 2) |
| Intelligent ≥2 | hooks | ◐ Phase 3 (by design) |
| Persistence | repositories | ✓ in-memory / ◐ DB Phase 2 |
| Exceptions list | 13 classes | ✓ (login/DB/network Phase 2) |
| OOP concepts | encapsulation `Incident`; abstraction interfaces; inheritance `User`; polymorphism states/strategies/`UserFactory`; composition `Incident`→notes; aggregation `Incident`→`User`/`ItService`; collections `Map`, `EnumSet`, `LinkedHashMap`, `List` | ✓ |
| ≥2 patterns | 5 | ✓ |
| ≥15 tests | 138 minimum | ✓ |

### 6.2 Cross-checks and fixes applied
| Check | Finding | Fix |
|---|---|---|
| Domain ↔ service | `assign` used to check technician before state, producing misleading errors | Added `Incident.ensureAssignable()` / `ensureUnassignable()` (pure, via the state) and put them **first** in #2–#4 |
| State ↔ service | Every one of the 11 actions maps to a service operation; `classify` only via create | ✓ table §J.1 "Triggered by" |
| State ↔ model | Notes/feedback/priority rules would have needed `switch`es in `Incident` | Moved to 4 state predicates (§J.2) |
| Priority ↔ model | `Incident` needs impact/urgency/score, `IncidentDraft` carries inputs, criticality comes from `ItService` | ✓ `IncidentFactory` builds `PriorityInput` |
| SLA ↔ model | Evaluator needs pause data and acknowledged/resolved times | Fields `pausedDuration`, `waitingSince`, `acknowledgedAt`, `resolution` and their getters exist; evaluator needs no policy (targets derived from `deadline − createdAt`) |
| SLA ↔ model | Reassign could erase the "response met" fact | `acknowledgedAt` kept on `unassign`, set only once |
| SLA ↔ service | Priority change after acknowledgement could flip response MET→BREACHED | #16 keeps the existing response deadline once acknowledged |
| Factory ↔ model | `Incident` ctor in `incident` takes `IncidentDraft`; draft first sat in `factory` → package cycle | **R2**: `IncidentDraft` moved to `domain.incident` |
| Factory ↔ SLA | Factory must not call SLA (A→B) | **Q-N2/R8**: service applies deadlines |
| Repository ↔ service | Workload needed by assignment | `countActiveByTechnicianId` + `IncidentStatus.countsTowardWorkload()` |
| Repository ↔ search | SLA status cannot be a repository filter | **R3** |
| Event ↔ service | Every mutating operation publishes ≥1 event; unassign, notes, feedback, failed assignment had no event | Added `IncidentStatusChanged(UNASSIGNED)`, `IncidentUpdated`, `AssignmentFailed` |
| Event ↔ Phase 3 | `sealed` interface would force editing a frozen file | **R1**: plain interface |
| Test ↔ contract | Every §H exception except `DuplicateIncidentException` has a test; all 12 valid + 87 invalid transitions; every service op in §T.2 | ✓ |
| Ownership ↔ conflicts | Only real conflict points: `pom.xml`, `docs`, test support | §S.2 rules; separate AI logs; B has its own test helpers |
| Dependency cycles | Package order §B.2 re-checked, acyclic | ✓ |
| A↔B build order | A's `Incident` needs B's `IncidentStates`; B's evaluator/service need A's `Incident` | Gates G0→G1→G2 in the manifest; B's states depend only on enums/exceptions |
| Hidden Phase 2 leakage | No Spring, JPA, security, notification, audit, scheduler, controller, DTO class in Phase 1 | ✓ |
| Decorative abstractions | CoR, `ReportNarrator`, `DuplicateDetector`, `SlaRiskDetector`, `NotificationFactory` | **R5/Q-N6**: not created |
| Spec vs Plan | Plan said exceptions "ITSMError", escalation "Admin", `A=backend/B=frontend` | Spec wins: `ItsmException`; spec's chain names kept; ownership per your split |
| Dead code | `DuplicateIncidentException`, `SlaWarning`, `SlaViolated` unused in Phase 1 | Accepted deliberately: spec-listed contracts consumed in Phase 3; documented as "defined, not published/thrown" |

### 6.3 Open risks (not blocking)
1. **Schedule:** Phase 2 + 3 are very tight after Phase 1; apply Plan §13 cut order (innovation first).
2. **Secret in history:** the committed DB password stays in Git history until history is cleaned; rotating it is mandatory either way.
3. **Test volume:** 138 minimum methods is ambitious; if time slips, reduce by priority (state matrix, priority oracle, SLA evaluator, lifecycle test are non-negotiable; everything else can drop to the spec's 15-case floor).

**Verdict: no unresolved contradictions. Contract is FROZEN on commit.**

---

## Deferred to Phase 2 (and later)
**Phase 2:** Spring Security + JWT + BCrypt + login/register + `InvalidLoginException`; JPA entities, mappers (`Incident.restore(...)`, additive), repositories, Flyway schema + seed, PostgreSQL/H2 profiles, springdoc; REST controllers, DTOs, `@RestControllerAdvice` with the §U.1 error format; `config` wiring (beans, `SwitchableAssignmentStrategy`); skill-based and load-based `AssignmentStrategy`; `Notification`, `NotificationChannel` (in-app, simulated e-mail), `NotificationFactory`, notification listener; `AuditEntry`, `AuditRepository`, audit listener (`critical() = true`); admin services (users, technicians, services, SLA policies, categories, strategy selection); `IncidentSearchService` (adds SLA-status filter); dashboard/report queries; DB/network exceptions; transactions and thread-safety review.
**Phase 3:** SLA monitor + scheduler + violation recording + `SlaWarning`/`SlaViolated` publishing; duplicate detection (`DuplicateIncidentException`); technician recommendation (via `rank`); SLA risk; escalation (Chain of Responsibility); keyword/LLM/fallback classifiers; report generator + narrator; SSE live dashboard.

## FINAL DO NOT CHANGE
Package structure · class/interface/enum/exception/record names · every public signature in §D.7 §F §L.2 §M §N §Q · `IncidentStatus` values and order · the 12 valid transitions and guard table · `Incident` composition/association layout · repository semantics (`Optional`, upsert, ordering, same-reference) · the 12 events' components and action constants · the 19 service operations, check order and role matrix · priority formula/bands/thresholds · SLA rules (24×7, pause, exact-deadline, 20 % warning) · error codes · the file ownership split. **Any change = joint agreement + a `contract-amendment:` commit.**
