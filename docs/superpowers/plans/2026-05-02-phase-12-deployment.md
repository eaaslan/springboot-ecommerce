# Phase 12 — Production Deployment Implementation Plan

**Goal:** Jib-built Docker images, GitHub Actions CI/CD pushing to GHCR, docker-compose.prod, Slack notifier, hardening doc. Final phase.

**Architecture:** Centralize Jib in parent pluginManagement, each service overrides image name. CI runs verify; CD runs jib:build with GHCR auth.

**Tech Stack:** jib-maven-plugin 3.4.4, eclipse-temurin:21-jre-alpine base, GitHub Actions, GHCR.

---

## Tasks

### P12.T1 — Spec + plan
- Files: spec, plan
- Commit: `docs(phase-12): production deployment spec/plan (Jib + GHA + Slack + hardening)`

### P12.T2 — Jib in parent + service activation
- Modify `pom.xml` (root): add `jib-maven-plugin` to `<pluginManagement>` with from/to/container defaults
- For each deployable service pom: simple `<plugin><artifactId>jib-maven-plugin</artifactId></plugin>` activation
- `shared/common` skipped (JAR not deployable)
- Verify with one local build: `mvn -DskipTests -pl services/order-service compile jib:dockerBuild`
- Commit: `feat(deploy): jib-maven-plugin in parent + per-service activation; multi-arch (amd64+arm64) eclipse-temurin:21-jre-alpine base`

### P12.T3 — CI workflow
- File: `.github/workflows/ci.yml`
- Triggers: push, pull_request; ubuntu-latest; setup-java temurin 21; m2 cache; mvn verify
- Commit: `ci: GitHub Actions CI workflow (mvn verify + Spotless gate)`

### P12.T4 — CD workflow
- File: `.github/workflows/cd.yml`
- Triggers: push to main; permissions packages:write; jib:build with GITHUB_TOKEN; tags sha + latest
- Commit: `ci(cd): GitHub Actions CD workflow (Jib build + push to GHCR on main)`

### P12.T5 — docker-compose.prod.yml
- File: `docker-compose.prod.yml`
- Sections: shared deps (postgres/redis/rabbit/kafka/prometheus/grafana/zipkin) + 10 app services pulling from GHCR
- Profile-based env: SPRING_PROFILES_ACTIVE=docker, host names: postgres / redis / etc.
- Commit: `feat(deploy): docker-compose.prod.yml — production-like local stack pulling from GHCR`

### P12.T6 — Slack notifier
- Files:
  - `services/notification-service/src/main/java/com/backendguru/notificationservice/notification/SlackNotifier.java`
  - Modify `NotificationService` to optionally call Slack after persisting Notification
  - `notification-service.yml` add `app.slack.enabled` + `app.slack.webhook-url`
- Use `RestTemplate` (already on classpath) or simple `HttpClient`
- Tests: SlackNotifier unit test stubs HTTP
- Commit: `feat(notification-service): Slack webhook notifier on order CONFIRMED (opt-in via app.slack.enabled)`

### P12.T7 — Hardening doc
- File: `docs/production-hardening.md`
- Sections: env var matrix, TLS, JWT rotation, dependency scanning, Oracle Cloud Ampere A1 walkthrough, secrets management, backup, prod observability, alerting examples
- Commit: `docs(production): hardening checklist + Oracle Cloud deploy walkthrough`

### P12.T8 — Spotless + verify + README + Turkish notes + tag
- mvn spotless:apply, mvn clean verify
- README: full rewrite top section — Project Complete summary, 12 phases ✅
- `docs/learning/phase-12-notes.md`: Jib vs Dockerfile, CI/CD pipelines, GHCR/registry auth, multi-arch, deployment topology options, interview Q&A
- Tag `phase-12-complete`, push

## Verification

1. `mvn clean verify` 14 modules
2. Local Jib build for order-service produces image (manual smoke, doc-only since no Docker needed in CI)
3. Tag pushed
