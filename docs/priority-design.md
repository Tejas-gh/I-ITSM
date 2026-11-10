# Incident Priority Calculation Design Specification — I-ITSM

| Attribute | Specification |
|---|---|
| **Subsystem** | Incident Prioritisation Subsystem |
| **Status** | FROZEN |
| **Version** | 1.0 (2026-11-09) |
| **Package** | `com.iitsm.domain.priority` |
| **Design Pattern** | Strategy Pattern (`PriorityStrategy`) |
| **Authoritative Source** | `docs/CONTRACT_FREEZE.md` (§K, §F.1, §G.1) |

---

## 1. Overview & Objective

The prioritisation subsystem calculates an objective, deterministic priority score and corresponding priority level (`P1`, `P2`, `P3`, or `P4`) for every incident logged into the system.

Priority calculation is governed by the **Strategy Pattern**, allowing alternative prioritization algorithms (e.g. historical SLA risk models or machine-learning scorers in Phase 3) to be swapped without modifying callers or domain entities.

---

## 2. Input Factors & Numerical Weights

The priority algorithm takes four factors encapsulated in `PriorityInput`:

| Factor | Source | Type / Range | Numeric Weight |
|---|---|---|---|
| **Impact** | Reporter (Employee / Admin) | `Impact` enum | `LOW` = 1, `MEDIUM` = 2, `HIGH` = 3, `CRITICAL` = 4 |
| **Urgency** | Reporter (Employee / Admin) | `Urgency` enum | `LOW` = 1, `MEDIUM` = 2, `HIGH` = 3, `CRITICAL` = 4 |
| **Business Criticality** | Inherent to `ItService` | `BusinessCriticality` enum | `LOW` = 1, `MEDIUM` = 2, `HIGH` = 3, `CRITICAL` = 4 |
| **Affected Users** | Reporter (Employee / Admin) | Integer (`int ≥ 1`) | Adds an additive bump based on volume bands |

> **Type Safety Note:** Separate enums are maintained for `Impact`, `Urgency`, and `BusinessCriticality` to prevent compile-time argument swapping, each exposing a uniform `int weight()` method returning 1..4.

---

## 3. Mathematical Formula & Scoring Algorithm

The default strategy, implemented in `WeightedPriorityStrategy`, computes the priority score and level as follows:

### 3.1 Base Product (Multiplicative Core)
$$\text{Base} = \text{Impact.weight()} \times \text{Urgency.weight()} \times \text{BusinessCriticality.weight()}$$
- Minimum possible base: $1 \times 1 \times 1 = 1$
- Maximum possible base: $4 \times 4 \times 4 = 64$
- Possible discrete base products: $1, 2, 3, 4, 6, 8, 9, 12, 16, 18, 24, 27, 32, 36, 48, 64$

### 3.2 Affected Users Additive Bump
$$\text{Bump} = \begin{cases} 
0 & \text{if } 1 \le \text{affectedUsers} \le 9 \\ 
4 & \text{if } 10 \le \text{affectedUsers} \le 49 \\ 
8 & \text{if } 50 \le \text{affectedUsers} \le 199 \\ 
12 & \text{if } \text{affectedUsers} \ge 200 
\end{cases}$$

*Validation rule:* `affectedUsers < 1` is rejected with `InvalidInputException("affectedUsers must be >= 1")`.

### 3.3 Final Composite Score
$$\text{Score} = \text{Base} + \text{Bump}$$
- Theoretical range: **1 to 76**

### 3.4 Priority Level Thresholds (Inclusive)
$$\text{Level} = \begin{cases} 
\text{P1 (Critical)} & \text{if } \text{Score} \ge 36 \\ 
\text{P2 (High)} & \text{if } 18 \le \text{Score} < 36 \\ 
\text{P3 (Medium)} & \text{if } 8 \le \text{Score} < 18 \\ 
\text{P4 (Low)} & \text{if } \text{Score} < 8 
\end{cases}$$

---

## 4. Oracle & Boundary Verification Table

The following test oracle table is binding and verified by `WeightedPriorityStrategyTest`:

| Impact | Urgency | Criticality | Users | Base | Bump | Score | Level | Test Case Classification |
|---|---|---|---|---|---|---|---|---|
| `CRITICAL` (4) | `CRITICAL` (4) | `CRITICAL` (4) | 1 | 64 | 0 | **64** | **P1** | Max base, no bump |
| `CRITICAL` (4) | `CRITICAL` (4) | `CRITICAL` (4) | 200 | 64 | 12 | **76** | **P1** | Theoretical maximum score |
| `HIGH` (3) | `HIGH` (3) | `CRITICAL` (4) | 1 | 36 | 0 | **36** | **P1** | Exact P1 inclusive boundary ($3 \times 3 \times 4$) |
| `CRITICAL` (4) | `CRITICAL` (4) | `MEDIUM` (2) | 1 | 32 | 0 | **32** | **P2** | Upper P2 without bump |
| `CRITICAL` (4) | `CRITICAL` (4) | `MEDIUM` (2) | 10 | 32 | 4 | **36** | **P1** | Bump lifts score across threshold (32 + 4 = 36) |
| `HIGH` (3) | `HIGH` (3) | `HIGH` (3) | 50 | 27 | 8 | **35** | **P2** | Upper P2 boundary just below P1 threshold |
| `MEDIUM` (2) | `HIGH` (3) | `HIGH` (3) | 1 | 18 | 0 | **18** | **P2** | Exact P2 inclusive boundary ($2 \times 3 \times 3 = 18$) |
| `HIGH` (3) | `HIGH` (3) | `LOW` (1) | 50 | 9 | 8 | **17** | **P3** | Upper P3 boundary just below P2 threshold |
| `MEDIUM` (2) | `MEDIUM` (2) | `MEDIUM` (2) | 1 | 8 | 0 | **8** | **P3** | Exact P3 inclusive boundary ($2 \times 2 \times 2 = 8$) |
| `LOW` (1) | `LOW` (1) | `HIGH` (3) | 10 | 3 | 4 | **7** | **P4** | Upper P4 boundary just below P3 threshold |
| `LOW` (1) | `LOW` (1) | `LOW` (1) | 1 | 1 | 0 | **1** | **P4** | Theoretical minimum score |

> **Correction Note (Row 7):** The contract freeze corrected an earlier label typo (`MEDIUM / MEDIUM / HIGH` which equals $2 \times 2 \times 3 = 12$, P3). The intended inclusive P2 boundary row is **`MEDIUM / HIGH / HIGH / 1`** ($2 \times 3 \times 3 = 18$, P2). The formula, weights, and thresholds are fully preserved.

### User Bump Band Boundaries

| User Count | Expected Bump | Notes |
|---|---|---|
| `0` | N/A | Throws `InvalidInputException` |
| `9` | `+0` | Upper limit of no-bump band |
| `10` | `+4` | Lower boundary of medium bump |
| `49` | `+4` | Upper boundary of medium bump |
| `50` | `+8` | Lower boundary of large bump |
| `199` | `+8` | Upper boundary of large bump |
| `200` | `+12` | Lower boundary of huge bump |

---

## 5. Architectural Contracts & Classes

### 5.1 `PriorityInput` (Record)
```java
package com.iitsm.domain.priority;

public record PriorityInput(
    Impact impact,
    Urgency urgency,
    BusinessCriticality businessCriticality,
    int affectedUsers
) {
    // Validates: non-null enums, affectedUsers >= 1 (throws InvalidInputException)
}
```

### 5.2 `PriorityResult` (Record)
```java
package com.iitsm.domain.priority;

public record PriorityResult(
    int score,
    PriorityLevel level
) {
    // Validates: score >= 1, level != null (throws InvalidInputException)
}
```

### 5.3 `PriorityStrategy` (Interface)
```java
package com.iitsm.domain.priority;

public interface PriorityStrategy {
    PriorityResult calculate(PriorityInput input);
}
```

### 5.4 `WeightedPriorityStrategy` (Implementation)
```java
package com.iitsm.domain.priority;

public final class WeightedPriorityStrategy implements PriorityStrategy {
    // Immutable, thread-safe, pure calculation
    // Throws InvalidInputException if input == null
}
```

---

## 6. Design Rationale & Quality Attributes

1. **Dampening Effect of Multiplication:** The multiplicative structure ensures that a low factor dampens the overall score. A cosmetic issue (`Impact.LOW`) on a mission-critical infrastructure service (`BusinessCriticality.CRITICAL`) yields a base of at most $1 \times 4 \times 4 = 16$, correctly preventing trivial defects from escalating to `P1`.
2. **Controlled Additive Scope:** Affected-user volume provides realistic breadth to prioritisation without allowing headcounts alone to overwhelm severity. The maximum bump (+12) can promote borderline incidents across a single threshold (e.g., $32 + 4 \rightarrow 36$ to achieve P1), but cannot elevate an intrinsically low-impact problem ($1 + 12 = 13$, remaining P3).
3. **Threshold Anchoring:**
   - **P1 (Score ≥ 36):** Requires at least three high factors ($3 \times 3 \times 4 = 36$).
   - **P2 (Score ≥ 18):** Anchored at medium-high severity ($2 \times 3 \times 3 = 18$).
   - **P3 (Score ≥ 8):** Anchored at all-medium severity ($2 \times 2 \times 2 = 8$).
   - **P4 (Score < 8):** Low severity and negligible impact.
4. **Deterministic & Side-Effect Free:** The calculation is purely functional. Given identical inputs, `calculate` always produces identical scores without reading system time or mutable state.
5. **Dynamic Recalculation:** When an incident's impact or urgency is updated via `updatePriorityInputs`, `IncidentService` recalculates the score using `service.getBusinessCriticality()` and `affectedUsers`, publishes `PriorityChanged`, and recomputes SLA deadlines.

