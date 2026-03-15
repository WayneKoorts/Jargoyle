# Jargoyle Backend

Spring Boot application powering the Jargoyle API.

## Prerequisites

- Java 25
- [Podman](https://podman.io/) (or Docker)

## Setup

1. Copy the environment file at the repo root and adjust if needed:
   ```bash
   cp ../../.env.example ../../.env
   ```

2. Start the full stack (app + PostgreSQL) from the repo root:
   ```bash
   podman compose --profile dev up --build
   ```

The application will be available at `http://localhost:8080`.

## Local Development

For day-to-day coding, run only the database in a container and launch the app from your IDE for faster restarts and debugger access:

```bash
podman compose --profile dev up db
```

Then start the application from your IDE with the Spring profile set to `dev`. The default database credentials in `application-dev.yml` match the container's defaults, so no extra configuration is needed unless you've changed them in `.env`.

## Gradle Commands

Run these from `src/backend/`:

```bash
./gradlew build      # Compile, test, and package
./gradlew test       # Run tests only
./gradlew bootRun    # Start the application
```

## API Testing (Dev Only)

When running with the `dev` profile, you can authenticate without going through OAuth by calling the dev login endpoint:

```
POST http://localhost:8080/api/dev/login
```

This creates a test user with the `USER` role and returns a session cookie (`JSESSIONID`). Include that cookie in subsequent requests to access authenticated endpoints. Most API clients (Postman, Insomnia, HTTPie, etc.) handle cookies automatically — just make sure cookie storage is enabled.

To test admin-only features, use the admin login endpoint instead:

```
POST http://localhost:8080/api/dev/login-admin
```

This creates a separate test user with the `ADMIN` role, granting access to `/api/admin/**` endpoints and the admin dashboard UI.

Both endpoints only exist in the `dev` profile. In production, they return 404.

## Stopping

Stop the containers with the same profile you used to start them:

```bash
podman compose --profile dev down
```

Add `-v` to also remove the database volume.
