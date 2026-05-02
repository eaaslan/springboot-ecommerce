# Phase 12 — Production Deployment (Jib + GitHub Actions + Slack)

> Hedef: Sistemi "laptop'umda çalışıyor" demekten "tek `git push`'la image çıkıyor, deploy edilebilir" demeye geçirmek. **Final phase** — roadmap'i kapatıyoruz.

---

## 1. Jib — Dockerfile Yazmadan Container Image

### Niye Dockerfile değil?

| | Dockerfile | Jib |
|---|---|---|
| Dosya | Ayrı dil (Dockerfile syntax) | Maven plugin config |
| Layering | Manuel optimize etmek gerek | Otomatik optimal (deps / classes / resources ayrı katman) |
| Build cache | Docker daemon | Maven layer cache |
| **Daemon gerekir mi?** | Evet (Docker engine) | **Hayır** — daemonless |
| Multi-arch | `docker buildx` ceremony | `<platforms>` element |
| Reproducibility | Bayt değişse her şey rebuild | Content-based deterministic |

**CI runner için kritik:** Docker-in-Docker setup yok. GitHub Actions runner doğrudan registry'e push edebiliyor.

### Bizim config (parent pom)

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
    </to>
    <container>
      <jvmFlags>
        <jvmFlag>-XX:MaxRAMPercentage=75</jvmFlag>
        <jvmFlag>-XX:+UseG1GC</jvmFlag>
      </jvmFlags>
    </container>
  </configuration>
</plugin>
```

`MaxRAMPercentage=75` — JVM container memory limit'in %75'ini heap için kullansın. Container OOM-killer'a karşı koruma.

### Goal'lar
- `jib:dockerBuild` — yerel Docker daemon'a image build (test için)
- `jib:build` — registry'e doğrudan push (CI için, daemon gerekmez)
- `jib:buildTar` — tar dosyası olarak çıkar (offline transfer için)

> **Mülakat sorusu:** "Jib base image olarak distroless mı temurin mi?"
> Cevap: Distroless daha küçük (50MB vs 80MB) ve security daha az saldırı yüzeyi. Ama JFR/heap dump için debug shell yok. Bizim eclipse-temurin:21-jre-alpine middle-ground — küçük + bash var.

---

## 2. CI/CD — GitHub Actions

### Pipeline Topology

```
[git push]
   │
   ├──▶ ci.yml: mvn verify (test + Spotless)
   │
   └─ if main ──▶ cd.yml: jib:build → GHCR push
                                       │
                                       ▼
                              ghcr.io/eaaslan/ecommerce-*:sha
                              ghcr.io/eaaslan/ecommerce-*:latest
```

### CI workflow

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
        with: { path: ~/.m2/repository, key: m2-${{ hashFiles('**/pom.xml') }} }
      - run: mvn -B clean verify
```

Spotless gate `verify` phase'in parçası — formatting bozuksa CI fail.

### CD workflow — GHCR Auth

GitHub Container Registry için **`GITHUB_TOKEN` otomatik** verilir (PAT yok). `permissions: packages: write` workflow yetkisi yeterli.

```yaml
- name: Login to GHCR
  run: echo "${{ secrets.GITHUB_TOKEN }}" | docker login ghcr.io -u ${{ github.actor }} --password-stdin

- name: Jib build + push
  run: |
    mvn -DskipTests \
      -Djib.to.auth.username=${{ github.actor }} \
      -Djib.to.auth.password=${{ secrets.GITHUB_TOKEN }} \
      -Djib.to.tags=${{ github.sha }},latest \
      compile jib:build
```

### Image Tagging

| Tag | Ne zaman | Production'da kullanım |
|---|---|---|
| `${{ github.sha }}` | Her commit | Immutable, traceable, **production'da bunu pin'le** |
| `latest` | Her main commit | Dev convenience, **production'da TEHLİKELİ** (silent change) |
| `phase-N` | Tag'lendiğinde | Versioning anchor |

> **Mülakat sorusu:** "Production deployment'ta `:latest` kullanır mısın?"
> Cevap: Hayır. `:latest` "tag" değil, sadece son push'a bir alias — silent rolling kicks. Pin commit SHA veya semver. Rollback için anchor lazım.

---

## 3. Multi-Arch Images

```xml
<platforms>
  <platform><architecture>amd64</architecture><os>linux</os></platform>
  <platform><architecture>arm64</architecture><os>linux</os></platform>
</platforms>
```

Tek manifest, iki platform. Apple Silicon dev (arm64) + AWS x86 prod aynı tag'i çekebilir, OS doğru image'ı seçer (manifest list).

**Niye önemli:** Oracle Cloud Ampere A1 = ARM64 (free-tier!). Apple M1/M2/M3 = ARM64. AWS Graviton = ARM64. Multi-arch artık standart.

> **Mülakat sorusu:** "Apple Silicon + cloud x86 deployment problemi?"
> Cevap: Mac'te `docker build` ile yapılan image ARM-only. x86 production'da çalışmaz. Çözüm: `docker buildx` veya Jib `<platforms>`. Multi-arch manifest çözer.

---

## 4. docker-compose.prod.yml

### Topology

```
[postgres] [redis] [rabbitmq] [kafka] [prometheus] [grafana] [zipkin]
   ↑          ↑          ↑          ↑          ↑           ↑          ↑
   │          │          │          │          │           │          │
   └────────┬─────────┬──────────┬─────┐  ┌──┴───────────┘          │
            ▼         ▼          ▼     ▼  ▼                          │
         [config-server] (depends)                                   │
               │                                                     │
               ▼                                                     │
        [discovery-server] ◀──────── all app services                │
               │                          register here              │
               └──────────────────────────────────┐                  │
                                                  ▼                  │
        [api-gateway] [user-svc] [product-svc] ... [catalog-stream] ─┘
              ↑
         port 8080 → external
```

`config-server` first (everything else depends on it), then `discovery-server`, then app services. Healthcheck-driven dependency:

```yaml
depends_on:
  config-server:
    condition: service_healthy
```

### Profile-driven config

| Profile | Logging | Service URLs | When |
|---|---|---|---|
| `dev` (default) | Plain text | `localhost` | `mvn spring-boot:run` |
| `docker` | JSON | service-name DNS (`postgres`, `kafka`) | `docker compose up` |
| `prod` | JSON | externally-set DNS | Real cloud |

Container env: `SPRING_PROFILES_ACTIVE=docker` → service name'e göre DNS resolve.

---

## 5. Slack Notifier — Pattern

### Opt-in design

```yaml
app:
  slack:
    enabled: ${SLACK_ENABLED:false}      # default off
    webhook-url: ${SLACK_WEBHOOK_URL:}
```

Set env vars to enable. Default safe — repo'yu klonlayıp çalıştıran rastgele biri Slack'e mesaj atmaz.

### Best-effort

```java
try {
  HttpResponse<String> resp = httpClient.send(request, ...);
  // log result + counter
} catch (Exception ex) {
  log.warn("Slack notify failed for order {}", event.orderId(), ex);
  // SWALLOW — order zaten CONFIRMED, notification fail rollback olmamalı
}
```

`slack.posted` ve `slack.failed{reason}` counter'lar Grafana'da. Failure rate yüksekse alarm.

### Production extension
- Webhook'u retry queue'ya yaz (Kafka, "fan-out" topic)
- Ayrı worker channel'lara dağıtsın (email, SMS, Slack, Discord)
- Bizim notification-service zaten "fan-out hub"; Slack tek transport olarak duruyor

> **Mülakat sorusu:** "Slack rate limit yer, ne yaparsın?"
> Cevap: Slack tier 1 = 1 msg/sec/channel. Bulk notification için: queue + scheduled worker, batch içinde tek mesajda toplama, dedicated app token (rate limit per app).

---

## 6. Production Hardening Checklist

[`docs/production-hardening.md`](../production-hardening.md) içinde detaylı liste. Ana başlıklar:

1. **Secrets externalization** — `.env`, K8s Secret, Vault. Asla repo'da.
2. **TLS termination** — Cloud LB / Caddy / Istio sidecar.
3. **JWT secret rotation** — rolling pattern (eski + yeni accept).
4. **Dependency scan** — OWASP, Dependabot, Trivy.
5. **Oracle Cloud Ampere A1 walkthrough** — free-tier 4-OCPU + 24GB ARM64.
6. **Backup** — `pg_dump` cron + S3 upload + DR test.
7. **Alertmanager rules** — error rate, latency, queue lag, cache hit ratio.
8. **Runbooks** — 5xx spike, cache miss, Kafka lag, saga compensation fail.
9. **Pre-prod smoke test** — checklist per release.
10. **Sign-off** — CI green + manual smoke + rollback plan.

---

## 7. Alternatif Deployment Stratejileri

### A — Single VM (bizim default)
- Oracle Cloud Ampere A1 free-tier
- Caddy + docker-compose.prod.yml
- Backup pg_dump cron
- Çok küçük tonajda gerçekten ücretsiz

### B — Kubernetes (Phase 13'e bırakılan)
- EKS / GKE / OKE / managed K8s
- Helm chart per service
- HorizontalPodAutoscaler — load'a göre replica
- ServiceMesh (Istio/Linkerd) — mTLS otomatik

### C — Serverless container (Cloud Run / ECS Fargate)
- No VM management
- Scale-to-zero
- Per-second billing
- Cold start latency ne kadar?

> **Mülakat sorusu:** "Bu sistemi nereye deploy edersin?"
> Cevap: Trafik küçükse VM (free-tier), orta-büyükse managed K8s, scale-to-zero gerekirse Cloud Run. Her use-case farklı; "doğru cevap" yok — trade-off açıkla.

---

## 8. Yaygın Tuzaklar

| Tuzak | Açıklama | Çözüm |
|---|---|---|
| `:latest` tag production | Silent rolling kicks | Pin SHA |
| Image içinde secret | `git secrets`, image scan yakalar | `.env` mount |
| Healthcheck eksik | Compose service'i "ready" sayar ama gerçek değil | `healthcheck:` zorunlu |
| Multi-arch unutmak | M1 dev → x86 prod fail | `<platforms>` |
| `MaxRAMPercentage` ayarsız | JVM container limit'i tanımaz, OOM | Java 10+ default OK ama explicit set |
| Graceful shutdown yok | Rolling deploy 502 | `server.shutdown=graceful` (Phase 11) |
| CI'da test skip | Kötü builds prod'a kaçar | CD'de `-DskipTests` ama CI'da değil |

---

## 9. Mülakat Cevapları — Hızlı Referans

**S: Jib vs Dockerfile?**
**C:** Jib daemonless (CI runner için ideal), otomatik optimal layering, multi-arch built-in, reproducibility. Dockerfile daha esnek (custom apt install vs.) ama Spring Boot için Jib enough + better.

**S: GHCR vs Docker Hub?**
**C:** GHCR GitHub repo ile entegre, `GITHUB_TOKEN` automatic auth, free public images. Docker Hub rate limit (anonim 100 pull/6h). Org-policy'e göre seçim.

**S: Multi-arch image neden gerekli?**
**C:** Apple Silicon (arm64) developers + cloud (x86 hâlâ baskın ama ARM Graviton/Ampere yaygın). Single-arch image cross-platform fail. Buildx veya Jib platforms list.

**S: Production'da hangi image tag kullanırsın?**
**C:** Immutable git SHA. `:latest` tehlikeli (silent change, no rollback anchor). Production manifest tam SHA pin'le.

**S: Rolling deployment nasıl?**
**C:** K8s Deployment strategy=RollingUpdate, maxUnavailable=0 maxSurge=1. Yeni pod ready olmadan eski pod kill edilmez. Phase 11'in graceful shutdown + readiness probe burada kritik.

**S: Blue-green vs canary?**
**C:** Blue-green = iki tam env; instant switch, hızlı rollback ama çift kaynak. Canary = küçük yüzde yeni versiyona, gradual ramp; daha güvenli ama observability discipline gerek. Bizim case: küçük scale + GitHub Actions = blue-green daha kolay.

**S: Secrets management seçenekleri?**
**C:** Compose .env (dev), K8s Secret (basic), HashiCorp Vault (rotation, audit log), AWS Secrets Manager (IAM-bound), GCP Secret Manager. Vault production-grade — secret rotation + dynamic secrets (DB user generate per session).

**S: Image scanning ne zaman?**
**C:** CD pipeline'da push öncesi. Trivy/Snyk image scan, high CVE → block. Image base'ini regular update et (Dependabot Docker support).

---

## 10. Phase 12 Çıktıları

- **Jib multi-arch** — parent pluginManagement, 12 servis aktivasyon, eclipse-temurin:21-jre-alpine base, amd64+arm64
- **GitHub Actions CI** — `mvn verify` + Spotless gate + m2 cache + surefire reports
- **GitHub Actions CD** — main branch → Jib push to GHCR (`ghcr.io/eaaslan/ecommerce-*:sha,latest`)
- **`docker-compose.prod.yml`** — production-like local stack pulling from GHCR (10 app + 7 deps + healthchecks)
- **Slack notifier** — opt-in webhook, best-effort, observability counters
- **Production hardening doc** — secrets, TLS, JWT rotation, dependency scan, Oracle Cloud Ampere A1 walkthrough, backup, alertmanager rules, runbooks, sign-off
- 14 modül `mvn clean verify` SUCCESS (~1dk 18s)
- Türkçe notlar: Jib vs Dockerfile, CI/CD pipelines, GHCR auth, multi-arch, image tagging, profile-based deploy, alternatif deployment stratejileri, mülakat Q&A
- **Tag:** `phase-12-complete`

---

## 11. Roadmap Tamam — Sıradaki

12 phase tamamlandı. Roadmap **biti**. Olası genişlemeler:

| Phase | Adı | Süre |
|---|---|---|
| 9.1 | Vector embeddings + RAG (pgvector + Spring AI EmbeddingClient) | 1 hafta |
| 10.1 | Reactive Kafka consumer (notification-service) | 3 gün |
| 13 | Kubernetes manifests + Helm chart | 1 hafta |
| 14 | GraalVM native image (startup ms, lower memory) | 3 gün |
| 15 | Frontend repo (vanilla JS, ayrı repo) | 2 hafta |
| 16 | E2E test suite (Cucumber/Karate) + load test (k6) | 1 hafta |

Bu liste açık-uçlu. Mülakat hazırlık veya gerçek bir feature ekleme yönünde gidebilir.

---

## 12. Final Stats

```
Modules:           14
Deployable services: 12
Phases:            12 ✅
Tests:             ~80
Lines of code:     ~12,000 (Java)
Docker services:   7 (postgres, redis, rabbitmq, kafka, prometheus, grafana, zipkin)
Tags:              phase-0 → phase-12 (13 tags)
Docs:              specs (12) + plans (12) + Turkish notes (12) + production-hardening
```

**Mülakat hazır.** Her phase için: spec doc, plan doc, çalışan kod, test'ler, Türkçe notlar (kavram + Q&A). System design whiteboard sorularına direkt referans veriyorsun.

🎉 Tebrikler — projeyi bitirdiniz.
