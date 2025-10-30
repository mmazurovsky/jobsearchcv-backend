# Repository Guidelines

## Project Structure & Module Organization
The backend is a Kotlin/Spring Boot service. Application sources live under `src/main/kotlin/com/jobsearchcv/backend`, grouped by feature (`controller`, `service`, `repository`, etc.). Shared DTOs and models sit in `domain`, while reusable helpers are in `util`. Tests mirror the main tree in `src/test/kotlin/com/jobsearchcv/backend`. Deployment scripts (`deploy.sh`, `docker-compose.yml`) and Gradle wrapper files are in the repo root.

## Build, Test, and Development Commands
- `./gradlew build`: compile, run unit tests, and create the application jar under `build/libs`.
- `./gradlew test`: execute the test suite without producing distribution artifacts.
- `./gradlew bootRun`: start the Spring Boot app using your local environment variables.
- `./run-local.sh`: convenience script that boots required dependencies (e.g., Dockerized services) before running the app.
- `docker-compose up`: launch the supporting infrastructure defined in `docker-compose.yml` for integration testing or manual QA.

## Coding Style & Naming Conventions
Follow Kotlin’s official style guide with four-space indentation. Use UpperCamelCase for classes (`SubscriptionAwareSchedulingService`), lowerCamelCase for functions and variables, and SCREAMING_SNAKE_CASE for constants. Prefer `val` over `var` and leverage data classes for immutable payloads. Keep package names lowercase (`com.jobsearchcv.backend.service`). When adding Gradle dependencies, group them logically in `build.gradle.kts`.

## Testing Guidelines
Tests use JUnit 5 with Spring test utilities. Place unit tests alongside peers in `src/test/kotlin/com/jobsearchcv/backend/<feature>`. Name test classes with the `*Test` suffix (`SubscriptionServiceTest`) and methods using descriptive sentences (`fun handlesDowngradeGracefully()`). Run `./gradlew test` before sending a PR; aim to cover new branches or failure modes introduced by your change.

## Commit & Pull Request Guidelines
Commit messages should follow the format `type: short description` (e.g., `fix: adjust scheduler downgrade path`). Keep commits focused on a single logical change. Pull requests should include:
1. Summary of the change and motivation.
2. Links to pertinent issues or tickets.
3. Testing notes (commands run, relevant logs).
4. Screenshots or sample payloads when modifying externally visible behavior (APIs, notifications).
Ensure the branch is rebased on the latest main and CI checks are green before requesting review.
