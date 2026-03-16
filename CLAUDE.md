# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Jargoyle is a document explanation tool — users upload documents (PDFs, images, text) and receive plain-English explanations with follow-up Q&A powered by AI (Spring AI + RAG).

The developer is experienced in other languages (e.g. C#) but learning Java and the Spring ecosystem. When making changes:
- **Explain as you go**: Explain the Spring/Java concepts, patterns, and trade-offs involved — not just *what* you're doing but *why*.
- **Prefer clear, explicit code** over clever abstractions. Avoid hiding complexity behind layers of indirection — the goal is for the code to be a readable reference.
- **Add brief comments** explaining *why* something works when the pattern might be unfamiliar (e.g. Spring annotations, security filter chains).
- **Flag gotchas and conventions** that differ from what a C# developer might expect (e.g. checked exceptions, annotation-driven DI, Gradle vs MSBuild idioms).

The full specification lives in `design/1-jargoyle-spec.md`. Phase-specific design documents (e.g. `design/2-file-upload.md`) cover implementation details for each milestone.

## Repository Structure

- `src/backend/` — Gradle multi-project build
  - `jargoyle-model/` — JPA entities, DTOs, enums
  - `jargoyle-repository/` — Spring Data JPA repositories
  - `jargoyle-service/` — Business logic, security resolution, storage, text extraction
  - `jargoyle-web/` — Spring Boot application (controllers, config, entry point)
- `src/frontend/` — React SPA (React 19, TypeScript, Vite, Tailwind CSS)
- `design/` — Project specification and design documents

## Tech Stack

**Backend**: Java 25, Spring Boot 4.0.3, Gradle 9.3.1 (Kotlin DSL), PostgreSQL (+ pgvector planned)
**Frontend**: React 19, TypeScript 5.9, Vite 7.3, Tailwind CSS 4.2

## Build Commands

All backend commands run from `src/backend/`:

```bash
./gradlew build                    # Compile, test, and package all sub-projects
./gradlew test                     # Run tests across all sub-projects
./gradlew :jargoyle-web:bootRun    # Start the application
./gradlew dependencies             # View dependency tree
```

Run a single test class:
```bash
./gradlew :jargoyle-service:test --tests "com.jargoyle.SomeTests"
```

Frontend commands run from `src/frontend/`:

```bash
npm install          # Install dependencies
npm run dev          # Start dev server (Vite)
npm run build        # Production build
npm run lint         # Run ESLint
npm run preview      # Preview production build locally
```

## Backend Architecture

- **Package root**: `com.jargoyle`
- **Entry point**: `jargoyle-web/.../JargoyleApplication.java`
- **Database migrations**: Flyway, migration files go in `jargoyle-web/src/main/resources/db/migration/`
- **Test naming**: `*Tests` suffix (e.g. `JargoyleApplicationTests`)
- **Integration tests**: Testcontainers with PostgreSQL

Auto-configuration for DataSource, Hibernate, and Flyway is excluded in `application.yml` until a database is configured. Remove those exclusions when PostgreSQL is available.

`SecurityConfig.java` in `jargoyle-web` configures OAuth2/OIDC login with a custom user service. It also enables `@EnableMethodSecurity` for `@PreAuthorize` support and enforces role-based access on `/api/admin/**` (requires `ADMIN` role).

Users have a `role` field (`Role` enum: `USER`, `ADMIN`) stored as a string in the database. The `CustomOidcUserService` injects the local role as a `GrantedAuthority` into the Spring Security context on login, so `hasRole('ADMIN')` works natively throughout the app.

## Conventions

- **British English** everywhere: code, comments, commit messages, documentation (e.g. "colour", "organisation", "initialise")
- **Unsigned commits are forbidden** — GPG/SSH signing must be available
- **Never commit unless explicitly asked** — "implement" or "fix" does not mean "commit"
- **Short, descriptive commit messages** committed in small, logical chunks
- Use the `product-owner` agent (via Task tool) for all GitHub issue/ticket operations
