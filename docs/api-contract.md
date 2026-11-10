# API & Domain Contract Specification — I-ITSM

| Attribute | Specification |
|---|---|
| **System** | Intelligent IT Service Management & Incident Resolution System (I-ITSM) |
| **Status** | FROZEN (Phase 1 Domain Contract / Phase 2 REST Specification) |
| **Version** | 1.0 (2026-11-09) |
| **Base Package** | `com.iitsm` (Java 21, Maven) |
| **Authoritative Source** | `docs/CONTRACT_FREEZE.md` (§U.1, §Q, §G, §H, §M) |

---

## 1. Scope & Architectural Boundaries

### 1.1 Phase 1 vs Phase 2 Distinction
- **Phase 1 (Current Core):** Focuses strictly on the domain core, application orchestration contracts (`IncidentService`, commands, `IncidentPermissionPolicy`), in-memory repositories, and unit tests. **No controllers, Spring web annotations, security filters, or DTO classes are built in Phase 1.**
- **Phase 2 (Future REST Integration):** Implements the REST API defined in this document under `/api`, using Spring MVC `@RestController`, DTO records, `@RestControllerAdvice`, and Spring Security JWT authentication. The authenticated JWT user identity is mapped directly to `actorId` for all service operations.

---

## 2. Roles & Authorization Matrix

The system defines 5 roles (`Role` enum) partitioned into 3 role groups:
- **Employee:** `Role.EMPLOYEE`
- **Technician:** `Role.TECHNICIAN`, `Role.TEAM_LEAD`
- **Administrator:** `Role.ADMINISTRATOR`, `Role.IT_MANAGER`

### Permission Matrix (§Q.2)

| Operation | Employee | Technician | Administrator | Constraint / Ownership |
|---|---|---|---|---|
| `CREATE` | ✓ | ✗ | ✗ | Only employees can report incidents |
| `VIEW` | ✓ | ✓ | ✓ | Employee: only own reported; Technician: only assigned to them; Admin: any |
| `ASSIGN` | ✗ | ✗ | ✓ | Manual assign, reassign, and auto-assign triggerable only by Admin |
| `ACKNOWLEDGE` | ✗ | ✓ | ✗ | Only the assigned technician |
| `START_WORK` | ✗ | ✓ | ✗ | Only the assigned technician |
| `ADD_INVESTIGATION_NOTE` | ✗ | ✓ | ✗ | Only the assigned technician |
| `REQUEST_INFO` | ✗ | ✓ | ✗ | Only the assigned technician |
| `RESPOND_INFO` | ✓ | ✗ | ✗ | Only the reporting employee |
| `ADD_ADDITIONAL_INFO` | ✓ | ✗ | ✗ | Only the reporting employee |
| `RESOLVE` | ✗ | ✓ | ✗ | Only the assigned technician |
| `VERIFY` | ✓ | ✗ | ✗ | Only the reporting employee |
| `REJECT_RESOLUTION` | ✓ | ✗ | ✗ | Only the reporting employee |
| `CLOSE` | ✗ | ✗ | ✓ | Only an administrator |
| `SUBMIT_FEEDBACK` | ✓ | ✗ | ✗ | Only the reporting employee (once per incident) |
| `UPDATE_PRIORITY` | ✗ | ✓ | ✓ | Administrator or the assigned technician |
| `SUGGEST_CATEGORY` | ✓ | ✓ | ✓ | Available to all authenticated roles |

*Note: Inactive users (`user.isActive() == false`) are strictly rejected with `UnauthorizedOperationException` across all operations.*

---

## 3. REST Endpoint Specifications (§U.1)

All endpoints reside under the `/api` prefix, consume/produce `application/json`, and are secured via JWT authentication (Phase 2). On success, endpoints return HTTP 200 OK or 201 Created with `IncidentResponse` (unless void or specialized).

| Method | Path | Allowed Roles | Service Operation | Description |
|---|---|---|---|---|
| `POST` | `/api/incidents` | Employee | `createIncident` | Creates a new incident; computes priority and SLA; runs initial auto-assignment. Returns 201 Created. |
| `GET` | `/api/incidents` | All | `listVisibleIncidents` | Lists incidents visible to the caller based on role matrix. Returns 200 OK. |
| `GET` | `/api/incidents/{id}` | All | `getIncident` | Retrieves incident details if visible to the caller. Returns 200 OK. |
| `POST` | `/api/incidents/suggest-category` | All | `suggestCategory` | Returns a category suggestion based on incident title and description. Returns 200 OK or 204 No Content. |
| `POST` | `/api/incidents/{id}/assign` | Admin | `assignIncident` | Manually assigns an incident to a specified technician. |
| `POST` | `/api/incidents/{id}/reassign` | Admin | `reassignIncident` | Reassigns an assigned or acknowledged incident to another technician. |
| `POST` | `/api/incidents/{id}/auto-assign` | Admin | `autoAssignIncident` | Triggers the automated assignment routine for a classified incident. |
| `POST` | `/api/incidents/{id}/acknowledge` | Technician (assigned) | `acknowledgeIncident` | Acknowledges the incident; records first acknowledgement for response SLA. |
| `POST` | `/api/incidents/{id}/start-work` | Technician (assigned) | `startWork` | Begins active investigation (`ACKNOWLEDGED -> IN_PROGRESS`). |
| `POST` | `/api/incidents/{id}/notes` | Technician (assigned) | `addInvestigationNote` | Appends an internal investigation note. |
| `POST` | `/api/incidents/{id}/request-info` | Technician (assigned) | `requestInfo` | Requests info from reporter; transitions to `WAITING_FOR_USER` and pauses SLA clock. |
| `POST` | `/api/incidents/{id}/respond-info` | Employee (reporter) | `respondToInfoRequest` | Reporter provides requested info; resumes active resolution (`IN_PROGRESS`). |
| `POST` | `/api/incidents/{id}/additional-info` | Employee (reporter) | `addAdditionalInfo` | Appends supplementary information from the reporter. |
| `POST` | `/api/incidents/{id}/resolve` | Technician (assigned) | `resolveIncident` | Submits resolution summary; marks resolution SLA met or breached. |
| `POST` | `/api/incidents/{id}/verify` | Employee (reporter) | `verifyResolution` | Confirms resolution satisfies requirements (`RESOLVED -> VERIFIED`). |
| `POST` | `/api/incidents/{id}/reject-resolution`| Employee (reporter) | `rejectResolution` | Reopens incident back to `IN_PROGRESS`; clears resolution record. |
| `POST` | `/api/incidents/{id}/close` | Admin | `closeIncident` | Permanently closes verified incident with optional closure note. |
| `POST` | `/api/incidents/{id}/feedback` | Employee (reporter) | `submitFeedback` | Submits reporter rating (1–5) and optional comment once resolved/verified/closed. |
| `PUT` | `/api/incidents/{id}/priority-inputs` | Admin, Technician (assigned) | `updatePriorityInputs` | Recomputes priority and SLA deadlines upon impact/urgency change. |

---

## 4. Request & Response Payloads

### 4.1 Create Incident Request (`POST /api/incidents`)
Corresponds to application command `CreateIncidentCommand` (§G.2).

```json
{
  "title": "VPN connection failure across corporate office",
  "description": "Users are unable to establish tunnel connections since 08:30 UTC.",
  "serviceId": "SVC_NETWORK",
  "categoryId": "NETWORK",
  "suggestedCategoryId": "NETWORK",
  "categorySource": "MANUAL",
  "impact": "HIGH",
  "urgency": "HIGH",
  "affectedUsers": 120
}
```
**Validation Rules:**
- `title`: Non-blank, 1..200 characters.
- `description`: Non-blank, 1..4000 characters.
- `serviceId`: Non-blank; must reference an active `ItService`.
- `categoryId`: Non-blank; must reference an active `Category`.
- `categorySource`: `MANUAL`, `KEYWORD`, or `LLM`. If `!= MANUAL`, `suggestedCategoryId` must be non-null and equal `categoryId`.
- `impact`: `LOW`, `MEDIUM`, `HIGH`, or `CRITICAL`.
- `urgency`: `LOW`, `MEDIUM`, `HIGH`, or `CRITICAL`.
- `affectedUsers`: Integer ≥ 1.

---

### 4.2 Assignment Request (`POST /api/incidents/{id}/assign` and `/reassign`)
Corresponds to application command `AssignIncidentCommand` (§G.2).

```json
{
  "technicianId": "TECH102"
}
```
**Validation Rules:**
- `technicianId`: Non-blank; user must exist, possess role `TECHNICIAN` or `TEAM_LEAD`, be active and available, and must not be overloaded (`activeIncidents < maxActiveIncidents`).
- For `/reassign`, `technicianId` must differ from the currently assigned technician.

---

### 4.3 Incident Text Action Request (`POST .../notes`, `/request-info`, `/respond-info`, `/additional-info`, `/resolve`)
Corresponds to application command `IncidentTextCommand` (§G.2).

```json
{
  "text": "Detailed investigative findings or requested information."
}
```
**Validation Rules:**
- `text`: Non-blank, 1..4000 characters.

---

### 4.4 Close Incident Request (`POST /api/incidents/{id}/close`)
Corresponds to application command `CloseIncidentCommand` (§G.2).

```json
{
  "closureNote": "Root cause identified as expired gateway certificate. Replaced and verified."
}
```
**Validation Rules:**
- `closureNote`: Optional/nullable. Stored as `null` if blank. Maximum 4000 characters.

---

### 4.5 Submit Feedback Request (`POST /api/incidents/{id}/feedback`)
Corresponds to application command `SubmitFeedbackCommand` (§G.2).

```json
{
  "rating": 5,
  "comment": "Prompt and effective resolution."
}
```
**Validation Rules:**
- `rating`: Integer between 1 and 5 (inclusive).
- `comment`: Optional/nullable, maximum 1000 characters. Normalised to empty string `""` if null.
- Can only be submitted once per incident in `RESOLVED`, `VERIFIED`, or `CLOSED` state.

---

### 4.6 Update Priority Inputs Request (`PUT /api/incidents/{id}/priority-inputs`)
Corresponds to application command `UpdatePriorityInputsCommand` (§G.2).

```json
{
  "impact": "CRITICAL",
  "urgency": "HIGH"
}
```
**Validation Rules:**
- `impact`: Non-null `Impact` enum value.
- `urgency`: Non-null `Urgency` enum value.
- Only permitted in states where `state.allowsPriorityChange() == true`.

---

### 4.7 Category Suggestion (`POST /api/incidents/suggest-category`)
**Request:**
```json
{
  "title": "Database connection pool timeout",
  "description": "Postgres connection pool exhausted on worker node 3."
}
```
**Response (200 OK):**
```json
{
  "categoryId": "DATABASE",
  "confidence": 0.85,
  "reason": "Matched keyword 'Postgres' and 'connection pool'",
  "source": "KEYWORD"
}
```
*Note: Returns 204 No Content if classifier produces no suggestion or suggested category is unknown/inactive.*

---

### 4.8 Unified Incident Response (`IncidentResponse`)
Returned by all incident query and mutation endpoints (§U.1):

```json
{
  "id": "INC1001",
  "title": "VPN connection failure across corporate office",
  "description": "Users are unable to establish tunnel connections since 08:30 UTC.",
  "reporterId": "EMP201",
  "serviceId": "SVC_NETWORK",
  "categoryId": "NETWORK",
  "suggestedCategoryId": "NETWORK",
  "categorySource": "MANUAL",
  "impact": "HIGH",
  "urgency": "HIGH",
  "affectedUsers": 120,
  "priority": "P1",
  "priorityScore": 44,
  "status": "IN_PROGRESS",
  "assignedTechnicianId": "TECH102",
  "createdAt": "2026-11-16T09:00:00Z",
  "responseDeadline": "2026-11-16T09:05:00Z",
  "resolutionDeadline": "2026-11-16T09:30:00Z",
  "acknowledgedAt": "2026-11-16T09:03:00Z",
  "resolvedAt": null,
  "verifiedAt": null,
  "closedAt": null,
  "notes": [
    {
      "authorId": "TECH102",
      "type": "INVESTIGATION",
      "text": "Checked edge firewall; authentication gateway is responsive.",
      "createdAt": "2026-11-16T09:08:00Z"
    }
  ],
  "resolution": null,
  "feedback": null
}
```

---

## 5. Error Format & HTTP Status Mapping

### 5.1 Standard Error JSON Payload (§U.1, §Q-M29)
All domain and application exceptions map to a standard JSON envelope:

```json
{
  "timestamp": "2026-11-16T09:12:00Z",
  "status": 409,
  "code": "INVALID_STATE_TRANSITION",
  "message": "Cannot close an incident that is RESOLVED",
  "details": []
}
```

### 5.2 Exception to HTTP Status Mapping (§U.1, §H)

| Exception Class | Error Code (`getCode()`) | HTTP Status | Description / Trigger Condition |
|---|---|---|---|
| `InvalidInputException` | `INVALID_INPUT` | **400 Bad Request** | Null/blank parameters, invalid email, draft invariant violations, out-of-range rating. |
| `UnauthorizedOperationException` | `UNAUTHORIZED_OPERATION` | **403 Forbidden** | Role permission matrix denial or inactive actor attempting an action. |
| `IncidentNotFoundException` | `INCIDENT_NOT_FOUND` | **404 Not Found** | Incident ID does not exist in repository. |
| `UserNotFoundException` | `USER_NOT_FOUND` | **404 Not Found** | User / actor ID does not exist in repository. |
| `ServiceNotFoundException` | `SERVICE_NOT_FOUND` | **404 Not Found** | Referenced IT Service ID does not exist. |
| `CategoryNotFoundException` | `CATEGORY_NOT_FOUND` | **404 Not Found** | Referenced Category ID does not exist. |
| `InvalidStateTransitionException` | `INVALID_STATE_TRANSITION` | **409 Conflict** | Illegal lifecycle action attempted for current status (e.g. `NEW -> close`). |
| `InvalidIncidentException` | `INVALID_INCIDENT` | **409 Conflict** | Guard violations (e.g. note or feedback in state where forbidden, duplicate feedback). |
| `TechnicianUnavailableException` | `TECHNICIAN_UNAVAILABLE` | **409 Conflict** | Target technician is marked unavailable or inactive. |
| `TechnicianOverloadedException` | `TECHNICIAN_OVERLOADED` | **409 Conflict** | Target technician active incidents ≥ `maxActiveIncidents`. |
| `DuplicateIncidentException` | `DUPLICATE_INCIDENT` | **409 Conflict** | Duplicate incident detected (Phase 3). |
| `MissingSlaException` | `MISSING_SLA` | **500 Internal Error** | No default or override SLA policy configured, or evaluating incident without deadlines. |
| *(Phase 2)* `InvalidLoginException` | `INVALID_LOGIN` | **401 Unauthorized** | Bad credentials during authentication. |

---

## 6. Phase 1 Domain Interfaces & Repositories

In Phase 1, business behavior is executed directly against domain aggregates and application service interfaces:

### 6.1 Aggregate Root: `com.iitsm.domain.incident.Incident`
- Enforces state transition legality via delegated `IncidentState`.
- Manages SLA deadline applications, pause accounting (`waitingSince`, `pausedDuration`), notes collection, resolution, and feedback.
- Pure state check guards: `ensureAssignable()` and `ensureUnassignable()`.

### 6.2 Repositories: `com.iitsm.domain.repository.*`
- `IncidentRepository`:
  - `Incident save(Incident incident)` (upsert keyed by id, same-reference storage)
  - `Optional<Incident> findById(String id)`
  - `List<Incident> findAll()`
  - `List<Incident> findByReporterId(String reporterId)`
  - `List<Incident> findByAssignedTechnicianId(String technicianId)`
  - `int countActiveByTechnicianId(String technicianId)` (counts only workload-eligible statuses)
  - `List<Incident> search(IncidentSearchCriteria criteria)` (conjunction AND of non-null criteria)
- `UserRepository`:
  - `User save(User user)`
  - `Optional<User> findById(String id)`
  - `List<User> findAll()`
  - `List<Technician> findAllTechnicians()`
- `ItServiceRepository`:
  - `ItService save(ItService s)`
  - `Optional<ItService> findById(String id)`
  - `List<ItService> findAll()`
- `CategoryRepository`:
  - `Category save(Category c)`
  - `Optional<Category> findById(String id)`
  - `List<Category> findAll()`
  - `List<Category> findAllActive()`
- `SlaPolicyRepository`:
  - `SlaPolicy save(SlaPolicy p)`
  - `Optional<SlaPolicy> findOverride(String serviceId, PriorityLevel priority)`
  - `Optional<SlaPolicy> findDefault(PriorityLevel priority)`
  - `List<SlaPolicy> findAll()`

### 6.3 Domain Event Contracts: `com.iitsm.domain.event.*`
Observer pattern contracts publishing 12 immutable event records covering all 7 audit fields (`occurredAt`, `actorId`, `incidentId`, `previousStatus`, `newStatus`, `action`, `description`). Published synchronously via `InMemoryDomainEventPublisher` strictly **after** repository persistence.

