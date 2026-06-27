# Repository Guidelines

## Project Structure & Module Organisation

Jargoyle is a document explanation tool with a Spring backend and React frontend. Source lives under `src/`; design notes are in `design/`.

- `src/backend/` is a Gradle multi-project build.
- `jargoyle-model/` contains JPA entities, DTOs, enums, validation, and types.
- `jargoyle-repository/` contains Spring Data repositories.
- `jargoyle-service/` contains business logic, storage, security helpers, and text extraction.
- `jargoyle-web/` contains the app, controllers, config, resources, Flyway migrations, and web tests.
- `src/frontend/` contains the React 19 + TypeScript + Vite SPA.

## Build, Test, and Development Commands

Run backend commands from `src/backend/`:

```bash
./gradlew build                    # Compile, test, and package all backend modules
./gradlew test                     # Run all backend tests
./gradlew :jargoyle-web:bootRun    # Start the Spring Boot app
./gradlew :jargoyle-service:test --tests "com.jargoyle.service.DocumentServiceTests"
```

Run frontend commands from `src/frontend/`:

```bash
npm install      # Install dependencies
npm run dev      # Start Vite dev server
npm run build    # Type-check and build production assets
npm run lint     # Run ESLint
```

For local services, use root `compose.yml`, e.g. `podman compose --profile dev up db`.

## Coding Style & Naming Conventions

Use Java 25, Spring Boot 4, Gradle Kotlin DSL, TypeScript, and React function components. Java code uses package root `com.jargoyle`, 4-space indentation, explicit classes, and clear Spring annotations. Keep comments short and focused on why a pattern matters. Frontend code follows the ESLint flat config and TypeScript settings. Use British English in code, comments, docs, and commit messages.

## Testing Guidelines

Backend tests use JUnit 5, Mockito where needed, and Testcontainers for database tests. Name Java test classes with the `*Tests` suffix, mirroring the package under test. Put module-specific tests in each module’s `src/test/java`. Run `./gradlew test` before submitting backend changes and `npm run lint && npm run build` for frontend changes.

## Commit & Pull Request Guidelines

Recent commits use short imperative summaries such as `Add DocumentChunk entity` or `Show documents as uncategorised...`. Keep commits small and signed; unsigned commits are not allowed. Do not commit unless explicitly asked. Pull requests should describe the change, link related issues or design notes, list test commands run, and include screenshots for visible UI changes.

## Security & Configuration Tips

Copy `.env.example` to `.env` for local configuration, but never commit secrets. Dev-only login endpoints are available only under the `dev` Spring profile. Database migrations belong in `jargoyle-web/src/main/resources/db/migration/` and should be additive Flyway migrations.
