# Jargoyle

*Guarding you from the fine print.*

Jargoyle is a document explanation tool. Upload everyday documents — phone bills, insurance policies, rental agreements, bank terms — and get a plain-English summary with key facts highlighted. Then ask follow-up questions in a conversational chat, with every answer grounded in your actual document.

## Tech Stack

**Backend:** Java 25, Spring Boot 4, Gradle (Kotlin DSL), PostgreSQL + pgvector, Spring AI, Flyway

**Frontend:** React 18, TypeScript, Vite, Tailwind CSS

## Getting Started

### Prerequisites

- Java 25
- PostgreSQL with the pgvector extension

### Build & Run

All commands run from `src/backend/`:

```bash
./gradlew build      # Compile, test, and package
./gradlew test       # Run tests only
./gradlew bootRun    # Start the application
```

## Repository Structure

```
jargoyle/
├── src/
│   ├── backend/     # Spring Boot application
│   └── frontend/    # React SPA
└── design/          # Specification and design documents
```

## Further Reading

See [`design/jargoyle-spec.md`](design/jargoyle-spec.md) for the full project specification.

## Licence

This project is licensed under the [GNU General Public Licence v3.0](LICENCE).
