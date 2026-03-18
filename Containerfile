# Stage 1: Frontend build
# Compiles the React SPA into static assets.
FROM node:22-alpine AS frontend

WORKDIR /frontend

# Copy package files first for dependency caching.
COPY src/frontend/package.json src/frontend/package-lock.json ./

RUN npm ci

# Copy source and build the production bundle.
COPY src/frontend/ .
RUN npm run build


# Stage 2: Backend build
# Uses the full JDK and Gradle wrapper to compile and package the application.
FROM eclipse-temurin:25-jdk-noble AS backend

WORKDIR /workspace

# Copy Gradle wrapper and build files first. This layer is cached separately
# from the source code, so dependency downloads only repeat when build files change.
COPY src/backend/gradle/ gradle/
COPY src/backend/gradlew .
COPY src/backend/settings.gradle.kts .
COPY src/backend/build.gradle.kts .
COPY src/backend/jargoyle-model/build.gradle.kts jargoyle-model/
COPY src/backend/jargoyle-repository/build.gradle.kts jargoyle-repository/
COPY src/backend/jargoyle-service/build.gradle.kts jargoyle-service/
COPY src/backend/jargoyle-web/build.gradle.kts jargoyle-web/

# Ensure the Gradle wrapper is executable (handles Windows CRLF line endings).
RUN chmod +x gradlew

# Download dependencies into the Gradle cache.
# The buildEnvironment task resolves all plugin and project dependencies
# across sub-projects without needing source code present.
RUN ./gradlew buildEnvironment --no-daemon

# Copy the backend source and build the fat JAR.
COPY src/backend/ .
RUN ./gradlew :jargoyle-web:bootJar --no-daemon


# Stage 3: Runtime
# Uses only the JRE — no compiler, no Gradle, no Node — for a smaller image.
FROM eclipse-temurin:25-jre-noble AS runtime

WORKDIR /app

COPY --from=backend /workspace/jargoyle-web/build/libs/jargoyle-web-*.jar app.jar

# Copy the built frontend assets into Spring Boot's static resources directory.
# Spring Boot automatically serves files from /BOOT-INF/classes/static/ inside the JAR,
# but it's simpler to place them alongside the JAR and configure an external static path.
# Instead, we inject them directly into the JAR's expected classpath location.
COPY --from=frontend /frontend/dist/ /app/static/

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar", "--spring.web.resources.static-locations=file:/app/static/,classpath:/static/"]
