# Intelligent IT Service Management & Incident Resolution System (I-ITSM)

An object-oriented IT Service Management system for reporting, classifying, prioritizing, assigning, resolving, verifying, and closing IT incidents.

The system provides role-based workflows for employees, technicians, team leads, administrators, and IT managers, with SLA monitoring, automatic escalation, intelligent incident features, notifications, audit tracking, and PostgreSQL persistence.

---

## Features

### Incident Management

- Create and track incidents
- Incident classification and categorization
- Impact, urgency, and priority management
- Technician assignment and reassignment
- Complete incident lifecycle management
- Additional-information requests and responses
- Investigation notes
- Resolution and resolution rejection/re-resolution
- Employee verification and feedback
- Incident closure

### Intelligent Features

- Keyword-based incident classification
- Duplicate incident detection
- Smart technician recommendation
- Automatic SLA-based escalation

### SLA Management

- Service-specific SLA policies
- Response and resolution targets
- SLA deadline tracking
- SLA warnings
- SLA violation detection
- Automatic escalation for eligible P1 incidents

### Notifications

- In-app notifications
- Assignment notifications
- Incident workflow notifications
- SLA warning and violation notifications
- Escalation notifications
- Resolution and closure notifications

### Audit & Search

- Complete incident audit trail
- State-transition tracking
- Server-side incident search
- Filtering by incident attributes
- Dashboard and reporting views

### Administration

- User management
- Technician management
- Technician availability
- Assignment strategy management
- SLA monitoring
- Incident monitoring
- Role-based access control

---

## Technology Stack

| Layer | Technology |
|---|---|
| Backend | Java 21 |
| Framework | Spring Boot 4.1.2 |
| Build | Maven Wrapper |
| Database | PostgreSQL 18.x |
| Persistence | Spring Data JPA / Hibernate |
| Database Migration | Flyway |
| Security | Spring Security + JWT |
| Frontend | React |
| Frontend Build Tool | Vite |
| Frontend Testing | Vitest + React Testing Library |
| Backend Testing | JUnit / Spring Boot Test |
| Development Environment | Windows 11 / VS Code |

---

## Architecture

```text
┌───────────────────────────────┐
│        React / Vite           │
│          Frontend             │
└───────────────┬───────────────┘
                │ REST API
                ▼
┌───────────────────────────────┐
│        Spring Boot            │
│           Backend             │
│                               │
│  REST API                     │
│  Application Services         │
│  Domain Model                 │
│  Business Rules               │
│  Security / RBAC              │
│  SLA Monitoring               │
│  Notifications                │
│  Audit / Search               │
└───────────────┬───────────────┘
                │ JPA / Hibernate
                ▼
┌───────────────────────────────┐
│          PostgreSQL           │
│                               │
│       Flyway Migrations       │
└───────────────────────────────┘
```

---

## Incident Lifecycle

The primary incident workflow is:

```text
NEW
 ↓
CLASSIFIED
 ↓
ASSIGNED
 ↓
ACKNOWLEDGED
 ↓
IN_PROGRESS
 ↓
WAITING_FOR_USER  ←→  IN_PROGRESS
 ↓
RESOLVED
 ↓
VERIFIED
 ↓
CLOSED
```

Resolution rejection can return an incident from `RESOLVED` to `IN_PROGRESS`, allowing the technician to investigate and resolve it again.

---

## Automatic Escalation

Automatic escalation applies to eligible unacknowledged P1 incidents.

The escalation chain is:

```text
Level 1
    ↓
TEAM LEAD

Level 2
    ↓
ADMINISTRATOR

Level 3
    ↓
IT MANAGER
```

Escalation is driven by the incident's configured response target.

For example, with a 5-minute response target:

```text
0 min   → Incident assigned
5 min   → Level 1 escalation
10 min  → Level 2 escalation
20 min  → Level 3 escalation
```

Once the incident is acknowledged, further automatic escalation stops.

---

## User Roles

The application supports the following roles:

| Role | Purpose |
|---|---|
| EMPLOYEE | Report and track incidents |
| TECHNICIAN | Investigate and resolve assigned incidents |
| TEAM_LEAD | Technician workflow plus escalated-incident intervention |
| ADMINISTRATOR | System and incident administration |
| IT_MANAGER | Management and escalated-incident intervention |

Access to operations is controlled through role-based authorization.

---

## Quick Start

### 1. Clone the Repository

```powershell
git clone https://github.com/Tejas-gh/I-ITSM.git
cd I-ITSM
```

### 2. Create PostgreSQL Database

Create:

```text
Database: iitsm
Username: postgres
Port: 5432
```

### 3. Configure Local Database Credentials

Create:

```text
backend/src/main/resources/application-local.yml
```

Example:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/iitsm
    username: postgres
    password: YOUR_POSTGRES_PASSWORD

  jpa:
    hibernate:
      ddl-auto: none
    open-in-view: false

iitsm:
  sla:
    monitor:
      enabled: true
```

Do not commit real passwords or other secrets.

### 4. Start the Backend

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Backend:

```text
http://localhost:8080
```

Flyway automatically applies the database migrations.

### 5. Start the Frontend

Open another terminal:

```powershell
cd frontend
npm install
npm run dev
```

Frontend:

```text
http://localhost:5173
```

For complete installation instructions and troubleshooting, see [`docs/setup-guide.md`](docs/setup-guide.md).

---

## Testing

### Backend

```powershell
cd backend
.\mvnw.cmd clean test
```

### Frontend

```powershell
cd frontend
npm install
npm run test -- --run
```

### Frontend Build

```powershell
npm run build
```

### Frontend Lint

```powershell
npm run lint
```

---

## Database Migrations

Flyway migrations are located in:

```text
backend/src/main/resources/db/migration/
```

Current migration history:

```text
V1__schema.sql
V2__seed_reference_data.sql
V3__notifications.sql
V4__credentials.sql
V5__incident_id_sequence.sql
V6__sla_alerts.sql
V7__escalations.sql
```

The migrations are applied automatically when the backend connects to the PostgreSQL database.

Do not remove or reorder existing migration files.

---

## Project Structure

```text
I-ITSM/
│
├── backend/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   └── resources/
│   │   │       └── db/migration/
│   │   └── test/
│   ├── pom.xml
│   ├── mvnw
│   └── mvnw.cmd
│
├── frontend/
│   ├── src/
│   ├── public/
│   ├── package.json
│   ├── package-lock.json
│   └── vite.config.js
│
├── docs/
│   ├── CONTRACT_FREEZE.md
│   ├── IMPLEMENTATION_MANIFEST.md
│   ├── api-contract.md
│   ├── priority-design.md
│   ├── setup-guide.md
│   ├── sla-and-lifecycle-design.md
│   ├── test-cases.md
│   └── diagrams/
│
├── .gitignore
├── .gitattributes
└── README.md
```

---

## Demo Accounts

The seeded environment provides demo accounts for the main application roles.

### Employee

```text
Username: emp1
Password: Employee@123
```

### Technician

```text
Username: tech1
Password: Technician@123
```

### Team Lead

```text
Username: teamlead1
Password: TeamLead@123
```

### IT Manager

```text
Username: itmanager1
Password: ITManager@123
```

Administrator credentials should be kept private and supplied separately where required.

---

## Documentation

Detailed project documentation is available in the `docs/` directory.

| Document | Purpose |
|---|---|
| `CONTRACT_FREEZE.md` | Frozen architecture and behavioral contracts |
| `IMPLEMENTATION_MANIFEST.md` | Implementation status and project mapping |
| `api-contract.md` | API contract documentation |
| `priority-design.md` | Priority calculation design |
| `sla-and-lifecycle-design.md` | SLA and incident lifecycle design |
| `setup-guide.md` | Complete installation and setup instructions |
| `test-cases.md` | Documented meaningful test cases |
| `diagrams/` | UML and system design diagrams |

---

## Security

The application includes:

- JWT-based authentication
- Role-based access control
- Password protection
- Protected REST APIs
- Authorization checks for incident operations
- Input validation
- Restricted administrative operations

Local credentials and secrets should remain outside version control.

---

## Development Notes

The project follows an object-oriented design with:

- Encapsulation
- Abstraction
- Inheritance
- Polymorphism
- Interfaces
- Composition and association
- Custom exception handling
- Collections
- Design patterns for domain behavior and assignment strategies

The system separates domain, application, persistence, API, and configuration responsibilities.

---

## License

This project was developed as an academic Object-Oriented Programming project.