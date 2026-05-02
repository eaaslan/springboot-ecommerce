# Phase 11 — Performance + Production-Readiness

> Hedef: Çalışan sistemi **production-grade** hale getirmek. Beş temel ekleme: idempotency, cache, rate limit, graceful shutdown, K8s probes. Her biri mülakatta direkt soru — şimdi pratik cevaba sahibiz.

---

## 1. Idempotency-Key — Network Retry'ları Güvenli Yap

### Problem
Client POST /api/orders → gateway timeout (network) → client retry → server **iki kere** order oluşturur, **iki kere** kart çeker. Klasik **dual-charge** bug'ı.

### Çözüm
Client her POST için unique header gönderir:
```
Idempotency-Key: 7c4f8e9a-...-uuid
```
Server `processed_orders` tablosunda `(key, user_id)` PK ile cache'ler:
```sql
CREATE TABLE processed_orders (
    idempotency_key  VARCHAR(80),
    user_id          BIGINT,
    order_id         BIGINT,
    response_body    TEXT,
    response_status  INT,
    created_at       TIMESTAMPTZ,
    PRIMARY KEY (idempotency_key, user_id)
);
```

### Akış
```java
if (key != null) {
  Optional<ProcessedOrder> cached = repo.findByIdempotencyKeyAndUserId(key, userId);
  if (cached.isPresent()) {
    return rebuildResponse(cached.get());  // 201 + same orderId
  }
}
OrderResponse resp = service.placeOrder(...);
repo.save(new ProcessedOrder(key, userId, resp.id(), serialize(resp), 201));
return resp;
```

### Kritik detaylar

**1. Composite key `(idempotency_key, user_id)`** — User A'nın key'i User B için de aynı olabilir. PK'da user_id olmazsa tenant leakage.

**2. Race condition** — İki request aynı anda `findByIdempotencyKeyAndUserId` yapsa, ikisi de "yok" görür → ikisi de saga başlatır. Çözüm: PK constraint loser'ı `DataIntegrityViolationException` ile yakalar; biz log + ignore.

**3. Same key + different body** — Stripe yaklaşımı: body hash'ini cache'e ekle, mismatch ise `409 Conflict`. Bizim MVP basitleştirdi.

**4. Retention** — 24h sonra TTL ile sil. Production'da `DELETE WHERE created_at < now() - interval '24 hours'` scheduled job.

> **Mülakat sorusu:** "Stripe'ın Idempotency-Key tasarımıyla karşılaştır."
> Cevap: Stripe 24h retention, response body cached, status code preserved, body hash check. Bizimki aynı pattern minus body hash. `(key, user_id)` composite extension cross-tenant leakage koruması.

---

## 2. Caffeine Local Cache — product-service Read Paths

### Niye local (Redis değil)?
- ~10k product × ~1KB = ~10MB heap → trivial
- Sub-microsecond hit latency (network hop yok)
- Redis cart-service için kullanılıyor — productlarda strong consistency gerekmiyor (5m staleness OK)
- Local cache **stampede protection** (LoadingCache built-in single-flight loader)

### Config
```yaml
spring:
  cache:
    type: caffeine
    cache-names: productById, productPage
    caffeine:
      spec: maximumSize=10000,expireAfterWrite=5m,recordStats
```

### Annotations
```java
@Cacheable(cacheNames = "productById", key = "#id")
public ProductResponse getById(Long id) { ... }

@Caching(evict = {
  @CacheEvict(cacheNames = "productById", key = "#id"),
  @CacheEvict(cacheNames = "productPage", allEntries = true)
})
public ProductResponse update(Long id, ProductUpdateRequest req) { ... }
```

### Cache invalidation stratejileri

| Strateji | Ne zaman | Trade-off |
|---|---|---|
| **Time-based (TTL)** | bizim case | Stale veri 5dk, basit |
| **Write-through (evict on write)** | bizim case | Doğru, ama instance-local — diğer instance'lar bilemez |
| **Event-based (Kafka)** | distributed cache | Tüm instance'lar invalidate eder, en doğru |
| **Read-through with versioning** | versiyonlu API | DB'den last_modified al, cache'le karşılaştır |

> **Mülakat sorusu:** "Cache stampede nedir?"
> Cevap: Cache miss anında binlerce concurrent request DB'yi hammerlar. Caffeine `LoadingCache` aynı key için tek seferde DB call yapar, diğerlerini bekletir = single-flight pattern. Redis'te aynı için `SET NX` lock pattern.

> **Mülakat sorusu:** "Cache invalidation neden zor?"
> Cevap: Distributed sistemlerde invalidation message kayıp olabilir. TTL safety net. Phil Karlton: "There are only two hard things..."

### Cache metrics
`recordStats=true` Micrometer'a aktarılır:
- `cache.gets{result="hit|miss"}` — hit ratio
- `cache.puts`, `cache.evictions`
- `cache.size`

Grafana panelinde **hit ratio** (`hit / (hit+miss)`) — düşükse cache yararsız (TTL çok kısa veya eviction çok agresif).

---

## 3. Rate Limiting — Token Bucket (Spring Cloud Gateway + Redis)

### Niye gerekli?
- Abuse protection (script kiddie 1000 req/s)
- Backend overload prevention (relay'i koru)
- Fair usage enforcement (free vs paid tier)

### Algoritma — Token Bucket
```
[bucket: 100 token]   ← her istek 1 token harcar
       ↑
   replenish: 50 tokens/sec
```
İstek geldiğinde `tokens > 0` → token harca, geç. `tokens == 0` → 429 Too Many Requests.

```yaml
spring:
  cloud:
    gateway:
      default-filters:
        - name: RequestRateLimiter
          args:
            redis-rate-limiter.replenishRate: 50    # 50 token/s
            redis-rate-limiter.burstCapacity: 100   # bucket size
            key-resolver: "#{@ipKeyResolver}"
```

`@ipKeyResolver` her IP için ayrı bucket. Redis'te bucket state Lua script ile atomik update.

### Algoritma karşılaştırması

| Algoritma | Davranış | Kullanım |
|---|---|---|
| **Token bucket** | Burst-friendly (bucket kadar burst, sonra sustained rate) | API gateway (Stripe, AWS) |
| **Leaky bucket** | Sabit rate (smoothing) | Network shaping |
| **Fixed window** | Saat 12:00–12:01: 100 req | Naive impl, edge effect (12:00:59 + 12:01:00 = burst) |
| **Sliding window** | Son 60s: 100 req | Hassas, biraz daha pahalı |

### Production tuning
- Per-user rate limit (auth gerekir): `key-resolver: "#{@userKeyResolver}"` — JWT'den userId
- Per-route override: route-level filters
- 429 response body: `Retry-After` header
- Tier-based: free user 50/s, paid 500/s — `KeyResolver` JWT claim'ine bakar

> **Mülakat sorusu:** "Token bucket'ı bir node'da mı global mi?"
> Cevap: Tek instance'sa local memory, distributed'da Redis (atomic Lua script veya Redisson). Bizim case: Redis. Yoksa horizontal scale'de her instance ayrı bucket = effective limit n × replenishRate.

---

## 4. Graceful Shutdown

### Setting
```yaml
server:
  shutdown: graceful
spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s
```

### SIGTERM aldığında
1. **Stop listening** — server yeni connection kabul etmez
2. **Drain inflight** — running request'leri bitirmeyi bekler (max 30s)
3. **Close pools** — DB, Redis, RabbitMQ connection'ları temiz kapat
4. **Exit 0** — temiz çıkış

### Niye kritik (K8s context)?
```
[K8s rolling update]
1. New pod started, ready
2. Old pod sent SIGTERM
3. Old pod 30s drain → tüm inflight bitsin
4. K8s sends SIGKILL after terminationGracePeriodSeconds (default 30s)
```

`server.shutdown=graceful` olmadan adım 3 yok → request mid-flight 502.

### Manifest tip (production)
```yaml
spec:
  terminationGracePeriodSeconds: 60
  containers:
    - name: order-service
      lifecycle:
        preStop:
          exec:
            command: ["sleep", "5"]   # let LB notice readiness=false first
```

> **Mülakat sorusu:** "Zero-downtime deployment'ı nasıl sağlarsın?"
> Cevap: 1) RollingUpdate strategy (maxUnavailable=0), 2) graceful shutdown, 3) preStop hook (LB'nin pod'u görmesi için 5s buffer), 4) readiness probe (yeni pod gerçekten hazırsa traffic alsın).

---

## 5. Liveness vs Readiness Probes

### Felsefe
| | Liveness | Readiness |
|---|---|---|
| Soru | "Process alive mi?" | "Şu anda traffic alabilir mi?" |
| Failed → | Pod **kill + restart** | Pod LB'den **çıkar** (kill yok) |
| Bağımlılık | App-level only | Critical downstream OK (DB, Redis) |
| Frequency | 10–30s | 3–5s (LB hızlı tepki) |

### Bizim config
```yaml
management:
  endpoint:
    health:
      probes:
        enabled: true
  health:
    livenessstate.enabled: true
    readinessstate.enabled: true
```

Endpoint:
- `GET /actuator/health/liveness` — sadece app durumu
- `GET /actuator/health/readiness` — app + bağımlılıklar (DB, Redis, vs)

### En yaygın hata
**Liveness DB-dependent yapma.** DB outage olursa:
- Liveness fail → pod restart → restart → restart...
- Bütün pod'lar dönmeye başlar → memory eksilir → cluster crash
- Halbuki problem **DB'de**, app'te değil

Liveness sadece "app process responsive mi" sorsun. DB readiness'a girer.

> **Mülakat sorusu:** "Liveness ve readiness farkı?"
> Cevap: Liveness = "ölü mü?" → restart. Readiness = "hazır mı?" → traffic on/off. Liveness app-only kontrol etmeli (deadlock, OOM); readiness downstream check eder (DB, Redis).

> **Mülakat sorusu:** "Startup probe ne zaman?"
> Cevap: K8s 1.16+. Slow-starting app için (app boot 60s sürüyorsa). Liveness/readiness `startup` probe başarılı olana kadar disable. JVM warm-up'lı uygulamalar için ideal.

---

## 6. Phase 11 İlişkili Notlar

### JVM Warm-up
JIT compiler kod path'leri profile etmesi için ilk N istek yavaş. Çözümler:
- **CDS (Class Data Sharing)** — class loading hızlandırma (`-XX:ArchiveClassesAtExit`)
- **AOT compilation (GraalVM native-image)** — startup ms, no JIT — Phase 12 konusu
- **Spring AOT** — ahead-of-time bean processing
- **Manual warm-up** — startup'ta key endpoints'i kendi-kendine çağır

### Connection pool tuning
- Hikari `maximum-pool-size` = `coreCount * 2 + spindleCount` (rule of thumb, ama actually CPU contention'a bak)
- R2DBC pool ayrı — initial-size + max-size
- Pool size **çok büyükse** thread context switch overhead, DB connection limit hit
- **Çok küçükse** queue, request timeout

### N+1 Audit
JPA `@EntityGraph(attributePaths = "...")` veya `JOIN FETCH` ile lazy load tuzaklarını önle. Hibernate stat logger açıp QPS ölç → spike'lar N+1 olabilir.

---

## 7. Mülakat Cevapları — Hızlı Referans

**S: Idempotency neden önemli?**
**C:** Network unreliability fact'ı. Client retry → server duplicate → para kaybı. Idempotency-Key + dedup table = at-least-once delivery + exactly-once effect.

**S: At-least-once vs exactly-once vs at-most-once?**
**C:** **At-most-once**: gönder, retry yok (loss riski). **At-least-once**: retry var (duplicate riski). **Exactly-once**: theoretical ideal — pratikte at-least-once + idempotency. Bizim sistem at-least-once + dedup = exactly-once-effective.

**S: Cache stampede nasıl önlenir?**
**C:** Caffeine LoadingCache built-in single-flight. Redis için `SET NX` lock + retry. Probabilistic early refresh (TTL'in %90'ında refresh) = thundering herd önler.

**S: Rate limit'i bypass etmek isteyen client?**
**C:** IP rotation — VPN, proxy. Çözüm: IP key yetersiz, JWT user-id bind et. Aşırı durumda CAPTCHA, WAF (Cloudflare).

**S: Graceful shutdown timeout 30s yetmezse?**
**C:** Long-running request varsa (örn. file upload). Async processing'e taşı (Kafka). Yoksa timeout artır + K8s `terminationGracePeriodSeconds` artır.

**S: Liveness probe'u DB'ye bağlamak kötü mü?**
**C:** Evet — DB outage = tüm pod restart loop = service degradation amplification. Liveness app-only, readiness downstream-aware.

**S: Caffeine vs Redis cache?**
**C:** **Caffeine** — local heap, sub-µs latency, instance-local (cross-instance staleness OK ise). **Redis** — distributed, network hop, cross-instance consistency. Hybrid (L1 Caffeine + L2 Redis) common.

**S: Production'da hangi metric'leri alarmlarsın?**
**C:** RED + business: error rate > 1%, p95 latency > SLO, queue depth > N, outbox FAILED count > 0, cache hit ratio < 50%, JVM heap > 80%, pod restart count > 0.

---

## 8. Phase 11 Çıktıları

- **Idempotency-Key middleware** — V3 `processed_orders` (composite PK), `IdempotencyService` capture/replay, OrderController header support
- **Caffeine cache** — `productById` + `productPage`, max 10k, TTL 5m, recordStats; @CacheEvict on create/update/softDelete
- **Gateway rate limit** — Redis token-bucket (replenishRate=50/s, burst=100, IP key); applied as `default-filter` to all routes
- **Graceful shutdown** — universal `application.yml` config-server'da: `server.shutdown=graceful`, 30s timeout
- **Liveness/readiness probes** — `/actuator/health/liveness` ve `/readiness` ayrı endpoint'ler her servis için
- 14 modül `mvn clean verify` SUCCESS (~1dk 8s)
- Türkçe notlar: idempotency design, cache invalidation strategies, rate-limit algorithms, K8s probes felsefesi, mülakat Q&A
- Tag: `phase-11-complete`

---

## 9. Sıradaki — Phase 12 (Final)

**Phase 12 — Production deployment:**
- **Jib** ile Docker image build (Dockerfile yazmadan)
- **Oracle Cloud Ampere A1** üzerinde deploy (free-tier ARM64)
- **Slack notification** — Spring Boot health hook → Slack webhook
- **CI/CD** — GitHub Actions: build, test, push image, deploy
- **Native image** (GraalVM) — startup ms, lower memory
- **Production hardening** review — secrets management, TLS, SSO
