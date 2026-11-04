# I-ITSM — Setup Guide

## 1. Project Overview

**Project:** Intelligent IT Service Management & Incident Resolution System (I-ITSM)  
**Repository:** https://github.com/Tejas-gh/I-ITSM  
**Backend:** Java 21, Spring Boot 4.1.2, Maven  
**Frontend:** React, Vite  
**Database:** PostgreSQL 18.6

This guide documents the development environment setup for Windows 11 using VS Code and PowerShell.

---

## 2. Prerequisites

| Software | Installed version |
|---|---|
| Java | Eclipse Temurin JDK 21.0.13 |
| Spring Boot | 4.1.2 |
| Maven | 3.10.0 (Maven Wrapper) |
| Node.js | 26.2.0 |
| npm | 11.13.0 |
| PostgreSQL | 18.6 |
| pgAdmin | Installed with PostgreSQL |
| Git | Installed |

VS Code is used as the development environment.

### Java installation and configuration

1. Download and install Eclipse Temurin JDK 21.
2. Enable the installer options to add Java to PATH and configure JAVA_HOME.
3. Set the JAVA_HOME environment variable to the JDK installation directory.
4. Add the JDK `bin` directory to the System Path, above Oracle Java entries if present.
5. Restart VS Code after changing environment variables.

Example paths:

```text
JAVA_HOME:
C:\Users\<username>\AppData\Local\Programs\Eclipse Adoptium\jdk-21.0.13.9-hotspot\

PATH:
C:\Users\<username>\AppData\Local\Programs\Eclipse Adoptium\jdk-21.0.13.9-hotspot\bin
```

Verify:

```powershell
java -version
where.exe java
$env:JAVA_HOME
```

The first Java executable returned by `where.exe java` should be the Temurin JDK 21 executable.

### Maven

A separate Maven installation is not required. The backend includes Maven Wrapper files:

- `mvnw`
- `mvnw.cmd`
- `.mvn/`

Verify from the backend directory:

```powershell
.\mvnw.cmd -version
```

---

## 3. Repository Setup

Clone the repository:

```powershell
git clone https://github.com/Tejas-gh/I-ITSM.git
cd I-ITSM
```

The root project structure is:

```text
I-ITSM/
├── README.md
├── AGENTS.md
├── .gitignore
├── .gitattributes
├── backend/
├── frontend/
└── docs/
    ├── design-doc.md
    ├── api-contract.md
    ├── conventions.md
    ├── test-cases.md
    ├── ai-log.md
    ├── setup-guide.md
    └── diagrams/
```

---

## 4. Backend Setup

### Technology and dependencies

The backend was generated using Spring Initializr.

Configuration:

- Project: Maven
- Language: Java
- Group: `com.iitsm`
- Artifact: `iitsm`
- Package: `com.iitsm`
- Packaging: Jar
- Java: 21
- Spring Boot: 4.1.2

Selected dependencies:

- Spring WebMVC
- Spring Security
- Spring Data JPA
- Validation
- PostgreSQL Driver
- H2 Database
- Flyway Migration

The generated Spring Boot 4 dependency names should be retained. Do not replace them with older Spring Boot 3 starter names.

### Backend structure

```text
backend/
├── pom.xml
├── mvnw
├── mvnw.cmd
├── .mvn/
└── src/
    ├── main/
    │   ├── java/com/iitsm/
    │   │   └── IitsmApplication.java
    │   └── resources/
    │       ├── application.yaml
    │       └── application-local.yml
    └── test/
```

### Build and compile

From the backend directory:

```powershell
.\mvnw.cmd clean compile
```

Run the application:

```powershell
.\mvnw.cmd spring-boot:run
```

The backend runs on:

```text
http://localhost:8080
```

---

## 5. PostgreSQL Setup

### Installation

1. Download PostgreSQL for Windows from the official PostgreSQL website.
2. Install PostgreSQL Server, pgAdmin 4 and Command Line Tools.
3. Stack Builder is optional.
4. Keep the default installation directory:

```text
C:\Program Files\PostgreSQL\18
```

5. Keep the default port:

```text
5432
```

6. Set a password for the `postgres` superuser during installation. Keep it private.

### Create the project database

1. Open pgAdmin 4.
2. Connect to PostgreSQL 18 using the password created during installation.
3. Right-click Databases.
4. Select Create → Database.
5. Enter:

```text
Database: iitsm
Owner: postgres
```

6. Save.

The database connection details are:

| Setting | Value |
|---|---|
| Host | localhost |
| Port | 5432 |
| Database | iitsm |
| Username | postgres |

### Configure the backend connection

Create:

```text
backend/src/main/resources/application-local.yml
```

Add:

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
```

Replace the password placeholder with the password configured during PostgreSQL installation.

**Important:** This file contains local credentials and must never be committed to Git.

The root `.gitignore` already excludes `application-local.yml`.

### Activate the local profile

The committed `application.yaml` contains:

```yaml
spring:
  application:
    name: iitsm
  profiles:
    active: local
```

This activates the local configuration when running the application.

### Database connection verification

Run:

```powershell
.\mvnw.cmd spring-boot:run
```

A successful connection produces log messages similar to:

```text
HikariPool-1 - Start completed.
Database: jdbc:postgresql://localhost:5432/iitsm
PostgreSQL 18.6
Initialized JPA EntityManagerFactory
Started IitsmApplication
```

Flyway currently has no migrations because the project schema has not yet been created. The empty-schema warning is expected at this stage.

---

## 6. Frontend Setup

### Technology

- React
- Vite
- React Router DOM
- Recharts
- Vitest
- React Testing Library

### Create the frontend

The frontend was generated using:

```powershell
npm create vite@latest frontend -- --template react
```

Install dependencies:

```powershell
cd frontend
npm install
npm install react-router-dom recharts
npm install -D vitest jsdom @testing-library/react @testing-library/jest-dom @testing-library/user-event
```

### Frontend structure

```text
frontend/
├── package.json
├── package-lock.json
├── vite.config.js
├── index.html
├── .env.example
└── src/
    ├── App.jsx
    ├── main.jsx
    ├── index.css
    └── test/
        └── setup.js
```

### Run the frontend

From the frontend directory:

```powershell
npm run dev
```

Vite runs at:

```text
http://localhost:5173
```

### Frontend verification commands

```powershell
npm run build
npm run lint
npm run test -- --run
```

The initial build and lint checks have passed. Vitest starts successfully, but there are no test files yet.

### API proxy

The Vite development server is configured to forward `/api` requests to:

```text
http://localhost:8080
```

This allows the frontend to communicate with the local Spring Boot backend.

---

## 7. Running the Complete Application

Open two VS Code terminals.

**Terminal 1 — Backend**

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

**Terminal 2 — Frontend**

```powershell
cd frontend
npm run dev
```

Open the frontend:

```text
http://localhost:5173
```

The backend is available at:

```text
http://localhost:8080
```

PostgreSQL must be running before starting the backend.

---

## 8. Git Workflow

Check changes:

```powershell
git status
```

Stage files:

```powershell
git add .
```

Commit:

```powershell
git commit -m "Describe your changes"
```

Push:

```powershell
git push
```

Pull the latest shared changes:

```powershell
git pull
```

Never commit:

- `application-local.yml`
- Real database passwords or JWT secrets
- `node_modules/`
- `backend/target/`
- `frontend/dist/`

The root `.gitignore` excludes these generated and sensitive files.

---

## 9. Current Setup Status

| Component | Status |
|---|---|
| Java 21 installation and PATH | Complete |
| Maven Wrapper | Complete |
| Spring Boot backend scaffold | Complete |
| PostgreSQL 18.6 installation | Complete |
| pgAdmin installation | Complete |
| `iitsm` database creation | Complete |
| Backend database connection | Verified |
| Backend clean compile | Successful |
| React + Vite scaffold | Complete |
| Frontend build | Successful |
| Frontend lint | Successful |
| Frontend test configuration | Complete; test cases pending |
| Database schema migrations | Pending |
| Application features | Pending |

**Note:** This guide describes the current development setup. Database tables, authentication, business logic, API endpoints and application functionality will be implemented in subsequent development phases.