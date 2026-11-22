# I-ITSM — Setup Guide

## 1. Project Overview

**Project:** Intelligent IT Service Management & Incident Resolution System (I-ITSM)  
**Repository:** https://github.com/Tejas-gh/I-ITSM  
**Backend:** Java 21, Spring Boot 4.1.2, Maven  
**Frontend:** React, Vite  
**Database:** PostgreSQL 18.6

This guide explains how to set up and run the I-ITSM application on Windows 11 using PowerShell.

The repository contains the complete backend, frontend, database migrations, seed data, tests, and project documentation.

---

## 2. Prerequisites

Install the following software before running the project:

| Software | Required |
|---|---|
| Java | JDK 21 |
| Spring Boot | 4.1.2 (included through Maven project configuration) |
| Maven | Maven Wrapper included |
| Node.js | Node.js 26.x recommended |
| npm | npm 11.x recommended |
| PostgreSQL | PostgreSQL 18.x |
| Git | Required to clone the repository |
| VS Code | Recommended development environment |

A separate Maven installation is **not required** because the backend includes the Maven Wrapper.

### Java

Install Eclipse Temurin JDK 21 or another compatible Java 21 JDK.

Verify the installation:

```powershell
java -version
where.exe java
$env:JAVA_HOME
```

The Java version should be 21.

If multiple Java installations are present, ensure that the Java 21 installation is the one used by the project.

---

## 3. Clone the Repository

Clone the repository:

```powershell
git clone https://github.com/Tejas-gh/I-ITSM.git
cd I-ITSM
```

The project structure is:

```text
I-ITSM/
├── README.md
├── .gitignore
├── .gitattributes
├── backend/
├── frontend/
└── docs/
    ├── CONTRACT_FREEZE.md
    ├── IMPLEMENTATION_MANIFEST.md
    ├── api-contract.md
    ├── priority-design.md
    ├── setup-guide.md
    ├── sla-and-lifecycle-design.md
    ├── test-cases.md
    └── diagrams/
```

---

# 4. PostgreSQL Setup

PostgreSQL is required for the normal application runtime.

## 4.1 Install PostgreSQL

Install PostgreSQL 18.x for Windows.

During installation:

- Install PostgreSQL Server.
- Install pgAdmin 4.
- Keep the default PostgreSQL port `5432`.
- Set a password for the `postgres` user.
- Keep the password private.

The default installation directory is typically:

```text
C:\Program Files\PostgreSQL\18
```

The exact installation directory may differ depending on the installation.

## 4.2 Create the I-ITSM Database

Open pgAdmin 4 and connect to the PostgreSQL server.

Create a database with:

```text
Database: iitsm
Owner: postgres
```

The expected connection details are:

| Setting | Value |
|---|---|
| Host | localhost |
| Port | 5432 |
| Database | iitsm |
| Username | postgres |
| Password | The password chosen during PostgreSQL installation |

No application tables need to be created manually.

---

# 5. Backend Configuration

## 5.1 Create the Local Configuration File

The local PostgreSQL password must not be committed to Git.

Create:

```text
backend/src/main/resources/application-local.yml
```

Use:

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

Replace:

```text
YOUR_POSTGRES_PASSWORD
```

with the password configured for the local PostgreSQL installation.

Do **not** commit this file if it contains a real password.

The repository's `.gitignore` excludes the local configuration file.

## 5.2 Spring Profile

The application is configured to use the local profile for normal development.

The local configuration supplies the PostgreSQL connection and local SLA-monitor configuration.

---

# 6. Database Migrations

The project uses Flyway for database schema management.

Migration files are located at:

```text
backend/src/main/resources/db/migration/
```

The repository currently contains migrations through V7:

```text
V1__schema.sql
V2__seed_reference_data.sql
V3__notifications.sql
V4__credentials.sql
V5__incident_id_sequence.sql
V6__sla_alerts.sql
V7__escalations.sql
```

When the backend starts for the first time, Flyway automatically applies the available migrations to the `iitsm` database.

This creates the required schema and reference/seed data.

**No manual table creation is required.**

If the database is empty, simply start the backend after creating the `iitsm` database.

---

# 7. Backend Setup and Run

Open PowerShell and enter the backend directory:

```powershell
cd backend
```

## 7.1 Verify Maven Wrapper

Run:

```powershell
.\mvnw.cmd -version
```

The Maven Wrapper downloads and uses the required Maven version automatically.

## 7.2 Compile the Backend

Run:

```powershell
.\mvnw.cmd clean compile
```

## 7.3 Run Backend Tests

To run the complete backend test suite:

```powershell
.\mvnw.cmd clean test
```

The project contains unit, integration, persistence, security, lifecycle, SLA, notification, search, and escalation tests.

## 7.4 Start the Backend

Run:

```powershell
.\mvnw.cmd spring-boot:run
```

The backend runs at:

```text
http://localhost:8080
```

On the first successful startup, Flyway applies the database migrations automatically.

PostgreSQL must be running before starting the backend.

---

# 8. Frontend Setup

The React/Vite frontend is already included in the repository.

You do **not** need to recreate the frontend.

Open a second PowerShell terminal:

```powershell
cd I-ITSM\frontend
```

## 8.1 Install Dependencies

Run:

```powershell
npm install
```

This installs the dependencies listed in `package.json` using `package-lock.json`.

Do not commit `node_modules/`.

## 8.2 Run the Frontend

Run:

```powershell
npm run dev
```

Vite normally starts the frontend at:

```text
http://localhost:5173
```

The Vite development server forwards `/api` requests to the Spring Boot backend.

Therefore, the backend should normally be running at:

```text
http://localhost:8080
```

while the frontend is running.

---

# 9. Frontend Verification

The frontend uses Vitest and React Testing Library.

Run the production build:

```powershell
npm run build
```

Run linting:

```powershell
npm run lint
```

Run the frontend test suite:

```powershell
npm run test -- --run
```

A successful build and test run confirms that the frontend is correctly configured.

---

# 10. Running the Complete Application

Use two PowerShell terminals.

### Terminal 1 — Backend

```powershell
cd I-ITSM\backend
.\mvnw.cmd spring-boot:run
```

The backend runs at:

```text
http://localhost:8080
```

### Terminal 2 — Frontend

```powershell
cd I-ITSM\frontend
npm run dev
```

The frontend runs at:

```text
http://localhost:5173
```

Open the frontend URL in a browser.

The complete application requires:

```text
PostgreSQL
    ↓
Spring Boot Backend
    ↓
React/Vite Frontend
```

---

# 11. Demo Accounts

The project includes seeded/demo users for testing the role-based workflows.

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

### Administrator

Use the administrator credentials configured for the supplied environment.

Do not commit private administrator credentials or other production secrets to the repository.

---

# 12. Main Application Features

The completed application includes the following major functionality:

- User registration and authentication
- JWT-based authentication
- Role-based access control
- Employee, Technician, Team Lead, Administrator and IT Manager workflows
- Incident creation and tracking
- Incident classification
- Priority calculation
- Technician assignment and reassignment
- Smart technician recommendation
- Duplicate incident detection
- Complete incident lifecycle management
- Additional-information requests and responses
- Investigation notes
- Resolution and resolution rejection/re-resolution
- Verification and feedback
- SLA calculation and monitoring
- SLA warnings and violations
- Automatic escalation
- In-app notifications
- Simulated notification/email mechanisms
- Audit trail
- Server-side incident search and filtering
- Dashboard and reporting
- Administrative user/technician management
- Assignment strategy management
- SLA monitoring
- PostgreSQL persistence

---

# 13. Troubleshooting

## PostgreSQL connection failure

Verify that:

1. PostgreSQL is running.
2. The database `iitsm` exists.
3. PostgreSQL is listening on port `5432`.
4. The username is `postgres`.
5. The password in `application-local.yml` is correct.

Expected connection URL:

```text
jdbc:postgresql://localhost:5432/iitsm
```

## Flyway migration failure

If Flyway reports a migration or schema error:

- Check that the `iitsm` database is accessible.
- Check the backend logs for the specific migration failure.
- Do not manually create the application tables.
- Do not delete migration files from `backend/src/main/resources/db/migration/`.

The migration history is part of the project's database design.

## Frontend cannot reach the backend

Verify that:

```text
Backend:  http://localhost:8080
Frontend: http://localhost:5173
```

and that the Spring Boot backend is running before using the frontend.

## Port already in use

If port `8080` is already occupied, stop the process using that port before starting the backend.

If port `5173` is occupied, Vite may automatically select another available development port.

---

# 14. Project Tests

### Backend

Run:

```powershell
cd backend
.\mvnw.cmd clean test
```

### Frontend

Run:

```powershell
cd frontend
npm run test -- --run
```

### Frontend build

```powershell
npm run build
```

### Frontend lint

```powershell
npm run lint
```

The project includes automated tests covering core business rules, lifecycle transitions, persistence, security/RBAC, notifications, SLA behavior, search, intelligent features, and automatic escalation.

---

# 15. Security and Files That Must Not Be Committed

Do not commit:

```text
application-local.yml
real PostgreSQL passwords
JWT secrets
node_modules/
frontend/dist/
backend/target/
```

The repository should contain configuration templates and source code, not personal credentials or generated build artifacts.

---

# 16. Documentation

Additional project documentation is available in:

```text
docs/
```

Important documents include:

- `CONTRACT_FREEZE.md` — frozen project architecture and behavioral contracts
- `IMPLEMENTATION_MANIFEST.md` — implementation status and project mapping
- `api-contract.md` — API contracts
- `priority-design.md` — priority calculation design
- `sla-and-lifecycle-design.md` — SLA and incident lifecycle design
- `test-cases.md` — documented meaningful test cases
- `diagrams/` — project design diagrams

---

## 17. Final Run Checklist

Before running the complete application, verify:

```text
[ ] Java 21 installed
[ ] PostgreSQL installed and running
[ ] Database "iitsm" created
[ ] application-local.yml created with local PostgreSQL password
[ ] Backend starts successfully
[ ] Flyway migrations complete successfully
[ ] Frontend dependencies installed with npm install
[ ] Frontend starts successfully
[ ] Backend tests pass
[ ] Frontend tests pass
[ ] Frontend build succeeds
```

Once these steps are complete, the I-ITSM application is ready to run locally.