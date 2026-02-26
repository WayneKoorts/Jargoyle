# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Jargoyle is a document explanation tool — users upload documents (PDFs, images, text) and receive plain-English explanations with follow-up Q&A powered by AI (Spring AI + RAG). It's a portfolio project demonstrating Spring Boot, Spring AI, OAuth2, and a React SPA.

The full specification lives in `design/jargoyle-spec.md`.

## Repository Structure

- `src/backend/` — Spring Boot application (Gradle, Java)
- `src/frontend/` — React SPA (not yet scaffolded)
- `design/` — Project specification and design documents

## Tech Stack

**Backend**: Java 25, Spring Boot 4.0.3, Gradle 9.3.1 (Kotlin DSL), PostgreSQL + pgvector
**Frontend** (planned): React 18, TypeScript, Vite, Tailwind CSS

## Build Commands

All backend commands run from `src/backend/`:

```bash
./gradlew build          # Compile, test, and package
./gradlew test           # Run tests only
./gradlew bootRun        # Start the application
./gradlew dependencies   # View dependency tree
```

Run a single test class:
```bash
./gradlew test --tests "com.jargoyle.SomeTests"
```

## Backend Architecture

- **Package root**: `com.jargoyle`
- **Entry point**: `JargoyleApplication.java`
- **Database migrations**: Flyway, migration files go in `src/main/resources/db/migration/`
- **Test naming**: `*Tests` suffix (e.g. `JargoyleApplicationTests`)
- **Integration tests**: Testcontainers with PostgreSQL

Auto-configuration for DataSource, Hibernate, and Flyway is excluded in `application.yml` until a database is configured. Remove those exclusions when PostgreSQL is available.

`SecurityConfig.java` is a temporary permit-all filter chain — replace it when implementing OAuth2.

## Conventions

- **British English** everywhere: code, comments, commit messages, documentation (e.g. "colour", "organisation", "initialise")
- **Unsigned commits are forbidden** — GPG/SSH signing must be available
- **Never commit unless explicitly asked** — "implement" or "fix" does not mean "commit"
- **Short, descriptive commit messages** committed in small, logical chunks
- Use the `product-owner` agent (via Task tool) for all GitHub issue/ticket operations
