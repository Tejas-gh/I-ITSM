# I-ITSM Project Instructions

## Project
- Intelligent IT Service Management & Incident Resolution System.
- Backend: Java 21, Spring Boot 3.5.x, Maven.
- Frontend: React, Vite, JavaScript.
- Database: PostgreSQL, Spring Data JPA, Flyway.

## Architecture
- Follow the agreed hybrid layered architecture: domain, application, infrastructure, api, config.
- Keep domain classes independent of Spring and framework-specific annotations.
- Controllers handle HTTP requests; business logic belongs in services and domain classes.
- Use DTOs for API requests and responses.
- Follow the existing package structure. Do not create unnecessary packages.

## Coding Rules
- Do not use Lombok.
- Prefer simple, readable, maintainable code over unnecessary abstraction.
- Use dependency injection.
- Inject Clock wherever time-dependent logic is required.
- Validate inputs and handle errors consistently.
- Do not introduce dependencies without approval.

## Design
- Implement the agreed incident lifecycle and role permissions.
- Use State, Strategy, Observer, Repository and Factory patterns where appropriate.
- Do not force design patterns where they add unnecessary complexity.
- Do not add features outside the agreed project scope without approval.

## API and Database
- Update docs/api-contract.md before changing API contracts.
- Use Flyway migrations for database schema changes.
- Never hardcode credentials or commit secrets.

## Testing
- Write tests for meaningful business behaviour.
- Test state transitions, priority, SLA, assignment and access control.
- Name tests according to the behaviour being tested.
- Do not claim tests pass unless they have actually been run.

## AI Collaboration
- Work on one module at a time.
- Respect existing code and interfaces; do not rewrite unrelated modules.
- Explain assumptions and report files changed.
- Do not generate placeholder code or unnecessary documentation.