# SLA & Incident Lifecycle Design

This document details the design, state machine, transition rules, and Service Level Agreement (SLA) calculations for the Intelligent IT Service Management & Incident Resolution System (**I-ITSM**).

---

## 1. Incident Lifecycle & State Pattern

The incident lifecycle models the full operational journey of an IT incident from reporting through resolution, verification, and closure. It is implemented using the **State Pattern** (`com.iitsm.domain.incident`).

### 1.1 State Pattern Architecture
* **Interface `IncidentState`:** Defines all allowable transition operations and state guard queries. Transitions are pure: calling a transition method returns the next state singleton or throws `InvalidStateTransitionException`.
* **Abstract Base `AbstractIncidentState`:** Provides default behavior: throws `InvalidStateTransitionException(currentStatus, actionName)` for every transition, and returns `false` for every guard predicate.
* **Concrete State Singletons:** 9 package-private states (`NewState`, `ClassifiedState`, `AssignedState`, `AcknowledgedState`, `InProgressState`, `WaitingForUserState`, `ResolvedState`, `VerifiedState`, `ClosedState`).
* **Factory / Lookup `IncidentStates`:** Exposes `initial()` returning `NewState.INSTANCE` and `of(IncidentStatus status)` covering all 9 enum values.

### 1.2 Valid State Transitions (12 of 99 pairs)

The lifecycle strictly permits only 12 valid transitions. All other 87 combinations throw `InvalidStateTransitionException`.

| Current State | Transition Action | Next State | Triggered By |
|---|---|---|---|
| `NEW` | `classify` | `CLASSIFIED` | `IncidentService.createIncident` (automated) |
| `CLASSIFIED` | `assign` | `ASSIGNED` | `assignIncident`, `autoAssignIncident`, create-time auto-assign |
| `ASSIGNED` | `acknowledge` | `ACKNOWLEDGED` | `acknowledgeIncident` (assigned technician) |
| `ASSIGNED` | `unassign` | `CLASSIFIED` | `reassignIncident` (admin re-routing) |
| `ACKNOWLEDGED` | `startWork` | `IN_PROGRESS` | `startWork` (assigned technician) |
| `ACKNOWLEDGED` | `unassign` | `CLASSIFIED` | `reassignIncident` (admin re-routing) |
| `IN_PROGRESS` | `requestInfo` | `WAITING_FOR_USER` | `requestInfo` (assigned technician queries reporter) |
| `IN_PROGRESS` | `resolve` | `RESOLVED` | `resolveIncident` (assigned technician provides fix) |
| `WAITING_FOR_USER` | `receiveInfo` | `IN_PROGRESS` | `respondToInfoRequest` (reporter provides details) |
| `RESOLVED` | `verify` | `VERIFIED` | `verifyResolution` (reporter confirms problem resolved) |
| `RESOLVED` | `rejectResolution` | `IN_PROGRESS` | `rejectResolution` (reporter reopens unresolved issue) |
| `VERIFIED` | `close` | `CLOSED` | `closeIncident` (admin formally closes incident) |

### 1.3 State Guard Predicates

In addition to state transitions, certain domain actions are guarded by the current state:

| State | `allowsInvestigationNotes()` | `allowsAdditionalInfo()` | `allowsFeedback()` | `allowsPriorityChange()` |
|---|:---:|:---:|:---:|:---:|
| `NEW` | ✗ | ✓ | ✗ | ✓ |
| `CLASSIFIED` | ✗ | ✓ | ✗ | ✓ |
| `ASSIGNED` | ✗ | ✓ | ✗ | ✓ |
| `ACKNOWLEDGED` | ✓ | ✓ | ✗ | ✓ |
| `IN_PROGRESS` | ✓ | ✓ | ✗ | ✓ |
| `WAITING_FOR_USER` | ✓ | ✓ | ✗ | ✓ |
| `RESOLVED` | ✗ | ✗ | ✓ | ✗ |
| `VERIFIED` | ✗ | ✗ | ✓ | ✗ |
| `CLOSED` | ✗ | ✗ | ✓ | ✗ |

* Attempting to perform a guarded action in an unsupported state throws `InvalidIncidentException`.

---

## 2. SLA Management Design

The SLA module (`com.iitsm.domain.sla`) calculates deadlines and evaluates breaches for response time and resolution time.

### 2.1 Default Service Level Agreements

Operating 24×7 without business-hour cutoffs:

| Priority | Response Target | Resolution Target | Description |
|---|---|---|---|
| **P1 — Critical** | 5 minutes | 30 minutes | Major system outage, widespread operational impact |
| **P2 — High** | 15 minutes | 2 hours | Core feature degraded, no immediate workaround |
| **P3 — Medium** | 1 hour | 8 hours | Minor functionality impaired, workaround available |
| **P4 — Low** | 4 hours | 24 hours | Cosmetic flaw or routine administrative query |

### 2.2 Policy Resolution (`SlaPolicyResolver`)
1. **Service Override:** The resolver first checks `SlaPolicyRepository` for a policy matching `(serviceId, priority)`.
2. **Default Policy:** If no service override exists, it looks for the default policy `(serviceId = null, priority)`.
3. **Missing Policy:** If neither exists, `MissingSlaException` is thrown before any incident mutation or event publishing occurs.

### 2.3 Deadline Calculation (`SlaCalculator`)
Deadlines are calculated relative to creation time `createdAt`:
$$\text{responseDeadline} = \text{createdAt} + \text{responseTarget}$$
$$\text{resolutionDeadline} = \text{createdAt} + \text{resolutionTarget}$$

### 2.4 Breach & Status Evaluation (`SlaEvaluator`)
The evaluator evaluates SLA status dynamically without mutating domain models:

#### Response SLA Evaluation:
* If `acknowledgedAt` is present:
  * If `acknowledgedAt <= effectiveResponseDeadline` $\to$ **`MET`**
  * Else $\to$ **`BREACHED`**
* If not yet acknowledged:
  * If `now > effectiveResponseDeadline` $\to$ **`BREACHED`**
  * If `now >= (effectiveResponseDeadline - responseTarget / 5)` (remaining time $\le 20\%$) $\to$ **`WARNING`**
  * Else $\to$ **`ON_TRACK`**

#### Resolution SLA Evaluation:
* If `resolvedAt` is present:
  * If `resolvedAt <= effectiveResolutionDeadline` $\to$ **`MET`**
  * Else $\to$ **`BREACHED`**
* If not yet resolved:
  * If `now > effectiveResolutionDeadline` $\to$ **`BREACHED`**
  * If `now >= (effectiveResolutionDeadline - resolutionTarget / 5)` (remaining time $\le 20\%$) $\to$ **`WARNING`**
  * Else $\to$ **`ON_TRACK`**

#### Resolution Clock Pause Rule (`WAITING_FOR_USER`):
While waiting for the reporter to provide additional information, the resolution clock is paused to ensure technicians are not penalized for user delays:
$$\text{effectiveResolutionDeadline} = \text{resolutionDeadline} + \text{pausedDuration} + \text{activeWait}$$
where:
$$\text{activeWait} = \begin{cases} \max(0, \text{now} - \text{waitingSince}), & \text{if } \text{waitingSince is present} \\ 0, & \text{otherwise} \end{cases}$$

When `receiveInfo` is called, `activeWait` is added to cumulative `pausedDuration` and `waitingSince` is reset to null.

#### Overall Status Precedence:
1. `BREACHED` (if either response or resolution is `BREACHED`)
2. `WARNING` (if either is `WARNING`)
3. `MET` (if both response and resolution are `MET`)
4. `ON_TRACK` (otherwise)
