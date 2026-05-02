# Phase 12 — Production Deployment Design (Jib + GitHub Actions + Slack)

## 1. Goal

Take the 14-module reactor from "runs locally with `mvn spring-boot:run`" to "**ships as Docker images via CI/CD with one git push**." Final phase — closes the roadmap.

Concrete deliverables:
1. **Jib** — Docker images without writing Dockerfiles. Multi-arch (amd64 + arm64).
2. **GitHub Actions CI** — every push/PR runs `mvn verify`.
3. **GitHub Actions CD** — main branch builds + pushes images to **GHCR** (GitHub Container Registry).
4. **`docker-compose.prod.yml`** — local "production-like" stack referencing remote images.
5. **Slack notifier** — order CONFIRMED → webhook (toggle-able).
6. **Hardening doc** — checklist: secrets, TLS, dependency scanning, manual Oracle Cloud Ampere A1 deploy walkthrough.

## 2. Why Jib?

| | Dockerfile | Jib |
|---|---|---|
| Author file | `Dockerfile` (separate language) | Maven plugin config in `pom.xml` |
| Layering | Manual (single layer easy, optimized hard) | Automatic optimal (deps / classes / resources separate) |
| Build cache | Docker daemon cache | Maven layer cache + reuse base image layers |
| Daemon needed | Yes (Docker engine) | **No** — Jib is daemonless (perfect for CI runners) |
| Multi-arch | `docker buildx` ceremony | `<platforms>` element |
| Reproducibility | Touch a byte → all layers rebuild | Deterministic layers by content |

Verdict for our project: **Jib wins.** Especially CI runners — no Docker-in-Docker needed.

## 3. Image Naming Scheme

```
ghcr.io/eaaslan/ecommerce-<service>:<tag>

# Tags:
# - <git-sha>   → every commit on main (CD pushes this)
# - latest      → always points to last main build
# - phase-N     → tag when phase done
```

Per service (14 modules → 10 deployable):
- `ecommerce-config-server`, `ecommerce-discovery-server`, `ecommerce-api-gateway`
- `ecommerce-user-service`, `ecommerce-product-service`, `ecommerce-cart-service`
- `ecommerce-inventory-service`, `ecommerce-payment-service`, `ecommerce-order-service`
- `ecommerce-notification-service`, `ecommerce-recommendation-service`, `ecommerce-catalog-stream-service`

`shared/common` is a JAR (not deployable). `springboot-ecommerce-parent` is just a pom.

## 4. Jib Plugin Config (Parent Pom)

```xml
<plugin>
  <groupId>com.google.cloud.tools</groupId>
  <artifactId>jib-maven-plugin</artifactId>
  <version>3.4.4</version>
  <configuration>
    <from>
      <image>eclipse-temurin:21-jre-alpine</image>
      <platforms>
        <platform><architecture>amd64</architecture><os>linux</os></platform>
        <platform><architecture>arm64</architecture><os>linux</os></platform>
      </platforms>
    </from>
    <to>
      <image>ghcr.io/eaaslan/ecommerce-${project.artifactId}</image>
      <tags><tag>${project.version}</tag><tag>latest</tag></tags>
    </to>
    <container>
      <jvmFlags>
        <jvmFlag>-XX:MaxRAMPercentage=75</jvmFlag>
        <jvmFlag>-XX:+UseG1GC</jvmFlag>
      </jvmFlags>
      <environment>
        <SPRING_PROFILES_ACTIVE>prod</SPRING_PROFILES_ACTIVE>
      </environment>
      <labels>
        <org.opencontainers.image.source>https://github.com/eaaslan/springboot-ecommerce</org.opencontainers.image.source>
      </labels>
    </container>
  </configuration>
</plugin>
```

`<from>` → minimal Alpine JRE 21. `MaxRAMPercentage=75` plays nice with K8s memory limits.

## 5. GitHub Actions — CI

`.github/workflows/ci.yml`:

```yaml
name: CI
on: [push, pull_request]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '21' }
      - uses: actions/cache@v4
        with:
          path: ~/.m2/repository
          key: m2-${{ hashFiles('**/pom.xml') }}
      - run: mvn -B clean verify
```

Spotless gate runs as part of `verify`. Everything fails fast.

## 6. GitHub Actions — CD

`.github/workflows/cd.yml`:

```yaml
name: CD
on:
  push:
    branches: [main]
jobs:
  publish:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write    # GHCR push
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '21' }
      - uses: actions/cache@v4
        with: { path: ~/.m2/repository, key: m2-${{ hashFiles('**/pom.xml') }} }
      - name: Login to GHCR
        run: echo "${{ secrets.GITHUB_TOKEN }}" | docker login ghcr.io -u ${{ github.actor }} --password-stdin
      - name: Build & push images via Jib
        run: |
          mvn -B -DskipTests \
            -Djib.to.auth.username=${{ github.actor }} \
            -Djib.to.auth.password=${{ secrets.GITHUB_TOKEN }} \
            -Djib.to.tags=${{ github.sha }},latest \
            compile jib:build
```

`jib:build` (vs `jib:dockerBuild`) goes daemonless straight to registry. Perfect for runners.

## 7. docker-compose.prod.yml

Two-pane stack:
- Infrastructure (postgres, redis, rabbit, kafka, prom/graf/zipkin) — same images as dev
- App services — pulled from GHCR via image: directive

Each service env-var configured for `prod` profile (no localhost — service-name DNS within compose network).

```yaml
services:
  config-server:
    image: ghcr.io/eaaslan/ecommerce-config-server:latest
    ports: ["8888:8888"]
    environment:
      SPRING_PROFILES_ACTIVE: docker
  ...
```

This is what someone could `docker compose -f docker-compose.prod.yml up -d` after fresh clone, no JDK needed.

## 8. Slack Notifier

`notification-service` already consumes OrderConfirmedEvent via AMQP + Kafka. Add a `SlackNotifier` component that, when `app.slack.enabled=true` and `app.slack.webhook-url` set, POSTs a markdown message:

```
🛒 Order #1234 confirmed
User 42 — 99.50 TRY at 2026-05-02T14:00:00Z
```

Default off — opt-in via env var. Production teams can wire to their `#orders` channel.

## 9. Production Hardening (doc-only)

`docs/production-hardening.md` covers:
- Env-var matrix per service (DB password, JWT secret, RabbitMQ creds, Kafka bootstrap, Slack webhook, etc.)
- TLS termination strategies (gateway-level vs sidecar)
- JWT secret rotation
- Dependency scanning (`mvn dependency-check:check` + GitHub Dependabot)
- Oracle Cloud Ampere A1 free-tier deploy walkthrough (4-OCPU + 24GB RAM ARM64 instance, perfect for our 12 services)
- Secrets management options (docker secrets, K8s secrets, HashiCorp Vault, AWS Secrets Manager)
- Backup strategies (pg_dump cron, Postgres logical replication)
- Observability in prod (alerting on metrics from Phase 8)

## 10. Out-of-Scope

| | Why deferred |
|---|---|
| Real Oracle Cloud deployment | Personal account; doc-only walkthrough |
| GraalVM native image | Spring AI MCP starter not native-ready in M6; Phase 12.1 |
| Kubernetes manifest / Helm chart | Cloud-specific; doc-only K8s example |
| Vault / SOPS secrets management | Org-policy-dependent |
| Trivy / Snyk scan integration | Mention in hardening doc |

## 11. Interview Talking Points

1. **Jib vs Dockerfile** — daemonless, automatic layering, multi-arch, reproducibility.
2. **CI vs CD** — CI = test gate, CD = ship. Separate workflows allow PR validation without registry push permission.
3. **GHCR auth** — `GITHUB_TOKEN` automatic, no PAT to manage. Workflow `permissions: packages: write`.
4. **Multi-arch images** — Apple Silicon (arm64) dev + x86 prod. Single image manifest with both.
5. **Image tagging strategy** — git-sha (immutable, traceable), `latest` (convenient, dangerous in prod), version (semver).
6. **Spring profiles** — `dev` (localhost), `docker` (DNS by name), `prod` (env-driven).
7. **MaxRAMPercentage** — JVM in container respects cgroup limit (Java 10+).
8. **Slack webhook over MQ** — fire-and-forget, simple. Production: post to internal queue, separate worker fans out to channels.

## 12. Acceptance Criteria

1. `mvn -DskipTests compile jib:dockerBuild -pl services/order-service` builds image to local Docker daemon.
2. `mvn clean verify` still green.
3. `.github/workflows/ci.yml` and `cd.yml` valid YAML — GitHub Actions parses cleanly.
4. `docker-compose.prod.yml` schema valid (`docker compose -f ... config`).
5. `app.slack.enabled=false` (default) — no Slack call. Setting `true` + URL → POST visible in network.
6. README has full project summary, all 12 phases ✅.
7. Tag `phase-12-complete` pushed.
