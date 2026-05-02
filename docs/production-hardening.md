# Production Hardening Checklist

This is the operator's punch list to take the project from "runs on a laptop" to "runs in front of customers." Each item is paired with the **why** so you can defend the choice in a review.

---

## 1. Secrets Externalization

Never commit secrets. Every sensitive value comes from an env var with a sensible default for dev only.

| Service | Required env vars (production) |
|---|---|
| `user-service` | `JWT_SECRET` (256-bit), DB creds in `application.yml` overrides |
| `api-gateway` | `JWT_SECRET` (matches user-service) |
| `order-service` | `RABBIT_USER`, `RABBIT_PASS`, `KAFKA_BOOTSTRAP` |
| `notification-service` | `SLACK_ENABLED`, `SLACK_WEBHOOK_URL` (if enabled), `RABBIT_*`, `KAFKA_BOOTSTRAP` |
| All DB-bound services | `<DB_NAME>_USER`, `<DB_NAME>_PASS` (current configs hard-code; replace before deploy) |
| Observability | `ZIPKIN_ENDPOINT`, `TRACING_SAMPLE_RATE` (lower in prod, e.g., 0.1) |

**Where they live:**
- Docker Compose / dev: `.env` file (gitignored)
- Kubernetes: `Secret` resource mounted as env
- AWS: Secrets Manager + IAM role
- GCP: Secret Manager + workload identity
- HashiCorp Vault: dynamic secrets, rotation

> **Anti-pattern:** baking secrets into Docker images or committing to git. Use `git secrets` / GitHub secret scanning to catch.

---

## 2. TLS Termination

Three options:

1. **Gateway-level** — Spring Cloud Gateway with cert-manager-issued cert. App ↔ app within VPC stays plaintext. Simplest.
2. **Service mesh sidecar** (Istio, Linkerd) — mTLS everywhere, automatic cert rotation. Best at K8s scale.
3. **CDN/load balancer** — CloudFront / AWS ALB / Cloudflare terminates TLS, talks plaintext to backend over private network. Most common.

Bizim default tercihi: **(3)** for cloud, **(1)** for self-hosted (Oracle Cloud Ampere). Mesh too heavy for this scale.

**HSTS header** ekle: `Strict-Transport-Security: max-age=31536000; includeSubDomains` — gateway response header filter.

---

## 3. JWT Secret Rotation

JWT shared secret değişirse: tüm aktif token'lar invalid olur (kullanıcı tekrar login).

### Rolling rotation pattern
1. Yeni secret üret, gateway + user-service'e env var olarak ekle (her iki secret'ı bilsin)
2. user-service yeni secret'la sign etmeye başlar; gateway eski + yeni secret'larla verify eder
3. Eski access token'ların max TTL'i (15 dk) dolduktan sonra eski secret'ı kaldır

Spring Security JWT validator'da `JwtParser` builder bir public key listesini destekler — yapılabilir.

**Alternatif — RS256 (asymmetric):** Public key'i tüm services'e dağıt, private key sadece user-service'te. Rotation public key listesi versiyonlanır. Production-grade.

---

## 4. Dependency Scanning

| Tool | Ne zaman |
|---|---|
| `mvn dependency-check:check` (OWASP) | CI'da haftalık |
| GitHub Dependabot | Otomatik PR (zaten free, aç) |
| Trivy / Snyk | Image-level scan, CD'den önce |
| Renovate | Dependabot alternatifi, daha güçlü grouping |

CI'a `mvn dependency-check:check` ekle — high/critical CVE → fail.

```yaml
- run: mvn dependency-check:check
- uses: actions/upload-artifact@v4
  with: { name: dep-check-report, path: target/dependency-check-report.html }
```

---

## 5. Oracle Cloud Ampere A1 Free-Tier Deploy Walkthrough

Always-Free tier: 4 OCPU + 24 GB RAM ARM64. **Sıfır maliyet** — bizim 12-servisi kaldırır.

### Adım 1 — Compute instance oluştur
- Region: Frankfurt veya Ankara (latency)
- Image: Ubuntu 22.04 ARM64
- Shape: `VM.Standard.A1.Flex` — 4 OCPU, 24 GB
- VCN: default
- SSH key: yerel `~/.ssh/id_rsa.pub`

### Adım 2 — Security List (firewall)
TCP ingress aç: 22 (SSH), 80, 443, 8080 (gateway), 15672 (RabbitMQ UI dev only), 3000 (Grafana dev only).

### Adım 3 — Instance prep
```bash
ssh ubuntu@<public-ip>
sudo apt update && sudo apt install -y docker.io docker-compose-plugin
sudo usermod -aG docker ubuntu
exit && ssh ubuntu@<public-ip>
```

### Adım 4 — Repo + secrets
```bash
git clone https://github.com/eaaslan/springboot-ecommerce
cd springboot-ecommerce
cat > .env <<EOF
JWT_SECRET=$(openssl rand -base64 32)
SLACK_ENABLED=true
SLACK_WEBHOOK_URL=https://hooks.slack.com/services/...
TRACING_SAMPLE_RATE=0.1
EOF
chmod 600 .env
```

### Adım 5 — GHCR'dan pull
```bash
echo "$GHCR_PAT" | docker login ghcr.io -u eaaslan --password-stdin
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
```

### Adım 6 — TLS (Caddy + Let's Encrypt)
```bash
sudo apt install -y caddy
sudo tee /etc/caddy/Caddyfile <<EOF
api.example.com {
  reverse_proxy localhost:8080
}
EOF
sudo systemctl reload caddy
```

Caddy otomatik ACME ile sertifika alır. HTTPS hazır.

---

## 6. Backup Strategy

PostgreSQL:
```bash
0 3 * * * pg_dump -h postgres -U user userdb | gzip > /backups/userdb-$(date +%F).sql.gz
0 3 * * * # repeat for each db
```

S3 / B2 / GCS upload (ücretsiz tier):
```bash
aws s3 cp /backups/ s3://my-backups/ --recursive --storage-class GLACIER_IR
```

Retention: günlük 7 gün, haftalık 4 hafta, aylık 12 ay.

**Disaster recovery test:** ayda 1 backup'ı staging'e restore et — gerçekten çalışıyor mu?

---

## 7. Production Observability Checklist

Phase 8'den taşıyoruz; production'da ek olarak:

- **Alertmanager rules** (`prometheus.rules.yml`):
  - `error_rate > 1% for 5m`
  - `p95 latency > 500ms for 5m`
  - `outbox_failed_total > 0` (any)
  - `notifications_duplicate_total / notifications_sent_total > 0.1`
  - `cache_hit_ratio < 0.5`
  - `kafka_consumer_lag > 1000`
  - `pod_restart_count > 3 in 1h`

- **PagerDuty / Opsgenie** entegrasyonu — ciddi alarmlar SMS/call
- **Slack** entegrasyonu — uyarı kanalına alarmlar

- **Log aggregation:** Loki (free), CloudWatch Logs, Datadog. Logstash JSON encoder zaten yapılandırılmış.

- **Distributed tracing sampling:** dev `1.0`, prod `0.1` (büyük volume'da). Tail sampling daha akıllı (Phase 8 notlarında).

---

## 8. Common Operational Issues + Runbooks

### Issue: 5xx spike
1. Grafana RED dashboard → hangi service?
2. Zipkin → trace_id'yle root cause span
3. Service log → trace_id ile arama
4. Rollback (latest → previous SHA) veya hotfix?

### Issue: cache hit ratio düşük
- TTL çok mu kısa?
- Eviction çok mu agresif (cache size yetersiz)?
- Cache key cardinality patladı mı?

### Issue: Kafka consumer lag
- Notification-service down mı?
- DB connection pool dolu mu?
- Throughput artmadı mı (yeni feature)?
- Add consumer instance.

### Issue: Saga compensation patladı
- `outbox_failed_total > 0`? Hangi event?
- Manuel order'ı CANCELLED'a geçir + müşteriyi bilgilendir
- Refund gerçekten oldu mu? Payment provider'da kontrol

---

## 9. Pre-Production Smoke Test Checklist

Deployment'tan önce:

- [ ] Health endpoints (`/actuator/health/readiness`) tüm servislerde 200
- [ ] Login → JWT alma → /api/users/me 200
- [ ] Catalog browse (anonim) çalışır
- [ ] Cart add → çıkar → temizle çalışır
- [ ] Order place (good card) → CONFIRMED + outbox PUBLISHED + notification log
- [ ] Order place (decline card 4111-...-1115) → CANCELLED, refund yok (charge fail)
- [ ] Idempotency replay: same key → same orderId
- [ ] Rate limit: 200 hızlı istek → bazıları 429
- [ ] Grafana dashboard veriler görünüyor
- [ ] Zipkin trace gateway → service'lerine kadar tam zincir
- [ ] Slack webhook (eğer açıksa) → kanala mesaj geldi
- [ ] Graceful shutdown: `kill -TERM` → 30s drain → exit 0

Bu listeyi geçtikten sonra "production'a hazır" diyebiliriz.

---

## 10. Sign-Off

Bir feature production'a çıkmadan önce:

- [ ] Tüm 14 modül `mvn clean verify` yeşil
- [ ] CI green on main
- [ ] CD pushed images to GHCR (sha + latest)
- [ ] Migration test: yeni Flyway migration'ı staging restore'da çalıştı
- [ ] Rollback plan documented (önceki SHA ne, manuel adımlar var mı)
- [ ] Smoke test geçti (yukarıdaki liste)
- [ ] On-call ekibi haberdar

Bu belge **canlı bir doküman** — her incident sonrası yeni runbook ekle, eskiyenleri güncelle.
