# Phase 10 — Reactive Layer (WebFlux + R2DBC)

> Hedef: Imperative `product-service` ile **yan yana** çalışan reactive bir read facade ekledik. `catalog-stream-service` (port 8089) — WebFlux + R2DBC + SSE. Aynı `productdb`'yi okur, ama tüm stack non-blocking. Mülakatta "WebFlux ne zaman kullanılır?" sorusuna direkt cevap.

---

## 1. Imperative vs Reactive — Mental Model

| | Imperative (Servlet/MVC) | Reactive (WebFlux) |
|---|---|---|
| Thread model | Thread-per-request (~200 thread) | Event loop (~CPU sayısı kadar thread) |
| Blocking I/O | Thread park olur, kaynak israfı | Thread park olmaz; callback queue'da bekler |
| Code style | Sync, imperative | Pipeline, declarative (`map`, `flatMap`) |
| Backpressure | Yok (server overwhelm) | Var (Reactive Streams spec) |
| DB driver | JDBC (blocking) | R2DBC (non-blocking) |
| Streaming | Servlet 3 async + WebSocket | First-class via Flux |

```
[Servlet model]                       [Reactive model]
client A → thread 1 → DB I/O wait     client A,B,C → 1 event loop thread
client B → thread 2 → DB I/O wait     ↓                ↓
client C → thread 3 → DB I/O wait     I/O outstanding  I/O outstanding
... 200 thread, sonra reject          ... 10k+ idle bağlantı OK
```

---

## 2. Java 21 Loom (Virtual Threads) — Modern Bağlam

2024+ önemli not: **Project Loom (virtual threads)** geldi. Servlet stack üzerinde virtual thread = 1 OS thread'in milyonlarca virtual thread'i taşıması mümkün. Yani "blocking I/O scaling" use case'i için WebFlux'a artık zorunluluk yok.

WebFlux ne zaman hâlâ değerli?
- **Streaming endpoint'ler** — SSE, WS, NDJSON
- **Reactive client API'ler** (Cassandra, MongoDB reactive driver)
- **Functional pipeline composition** (Reactor `merge`, `zip`, `retryWhen`)
- **Backpressure** kritik (publisher faster than consumer)

Pure CRUD app + Loom = Servlet/MVC daha basit, debug kolay, ekosistem geniş.

> **Mülakat sorusu:** "Loom geldi, WebFlux öldü mü?"
> Cevap: Hayır. Loom blocking-I/O scaling problemini Servlet'te de çözer, doğru. Ama WebFlux'ın streaming API'si, backpressure, declarative composition güçlü kalır. "Ne zaman" sorusu önemli — projeyi şekline bak.

---

## 3. Mono ve Flux

`Mono<T>` = 0 veya 1 element üreten Publisher (`Optional<T>` benzeri ama async).
`Flux<T>` = 0 ile N element üreten Publisher (`Stream<T>` benzeri ama async + backpressure).

```java
Mono<ProductRow> byId   = repository.findById(1L);     // 0 or 1
Flux<ProductRow> all    = repository.findAll();         // many
Flux<ProductRow> stream = Flux.interval(Duration.ofSeconds(2))
                              .concatMap(t -> findByEnabledTrue(...));
```

### Lazy execution
```java
Mono<String> m = Mono.fromCallable(() -> { System.out.println("hi"); return "x"; });
// "hi" YAZILMAZ — pipeline subscribe olmadan akmaz
m.subscribe();    // ŞİMDİ yazar
```

WebFlux framework'ü subscribe'ı senin için yapar (HTTP response yazılırken).

> **Mülakat sorusu:** `Mono.just()` vs `Mono.fromCallable()`?
> Cevap: `just()` argümanı eagerly evaluate eder. `fromCallable()` lazy — pipeline subscribe edildiğinde yürütülür. Side-effect olan kod için `fromCallable` kullan, yoksa subscribe öncesi çalışır.

---

## 4. R2DBC — Non-Blocking DB Driver

JDBC her query'de thread'i bloklar (network'ten cevap gelene kadar park). R2DBC reactive Streams uyumlu — query "tamamlanınca" callback patlar, thread bekleyişte değil.

| | JDBC | R2DBC |
|---|---|---|
| Spec | JDBC 4.x | Reactive Streams 1.0 |
| Blocking | Yes | No |
| Postgres support | ✅ official | ✅ `r2dbc-postgresql` |
| Oracle support | ✅ | partial |
| ORM | Hibernate | Spring Data R2DBC (lighter) |
| Migration tool | Flyway | Flyway can run JDBC migrations against same DB |

Bizim case: R2DBC connection productdb'ye, ama Flyway migration'ları imperative product-service'in JDBC'siyle koşuyor. Schema sahipliği product-service'te kalıyor; biz read-only consumer.

```java
// R2DBC repository
public interface ProductReactiveRepository extends ReactiveCrudRepository<ProductRow, Long> {
  Flux<ProductRow> findByEnabledTrue(Pageable pageable);

  @Query("SELECT * FROM products WHERE enabled = true AND name ILIKE '%' || :q || '%' LIMIT :limit")
  Flux<ProductRow> searchEnabled(String q, int limit);
}
```

Spring Data Reactive — repository signature `Flux<T>` veya `Mono<T>` olur. JPA gibi auto-derived query, ama proxy reactive.

> **Mülakat sorusu:** R2DBC ile Hibernate Reactive farkı?
> Cevap: R2DBC = düşük seviye, JdbcTemplate'in reactive eşi (manual SQL veya Spring Data R2DBC). Hibernate Reactive = ORM (lazy load, dirty tracking) reactive üzerinde — ama operationally daha karmaşık. Bizim case basit; R2DBC tercih.

---

## 5. Server-Sent Events (SSE)

```java
@GetMapping(value = "/products/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ProductRow> stream(@RequestParam int intervalSeconds) {
  return Flux.interval(Duration.ofSeconds(intervalSeconds))
             .onBackpressureDrop()
             .concatMap(t -> repository.findByEnabledTrue(PageRequest.of(0, 5)));
}
```

`text/event-stream` content-type → tarayıcı/curl bağlantıyı **açık tutar**. Server `data: {...}\n\n` formatında JSON yollar. Servlet'te bunu yapmak için async + custom `ResponseBodyEmitter` lazım. WebFlux'ta `Flux<T>` + content-type yeterli.

```bash
curl -N http://localhost:8089/api/catalog/products/stream?intervalSeconds=2
data: {"id":1,"sku":"...","name":"..."}

data: {"id":2,...}
```

Use case: realtime dashboard, stock fiyat ticker, order status updates, AI chat streaming.

> **Mülakat sorusu:** SSE vs WebSocket?
> Cevap: SSE = server→client one-way, HTTP üzerinde, basit. WebSocket = bidirectional, ayrı protokol, daha güçlü ama daha karmaşık. Read-only push için SSE, chat için WebSocket.

---

## 6. Backpressure

Producer N/sn üretir, consumer M/sn tüketir. M < N ise mesajlar buffer'da birikir → OOM. Reactive Streams çözüm: **demand signal**.

Consumer "request(50)" der → publisher 50 üretir → consumer "request(50)" → ...

```java
// Stratejiler
flux.onBackpressureBuffer(1000)    // queue 1000, sonra error
flux.onBackpressureDrop()          // overflow → düşür
flux.onBackpressureLatest()        // sadece en son tut
flux.onBackpressureError()         // overflow → error
```

Bizim SSE'de `onBackpressureDrop()` — yavaş client overflow ederse mesaj düşer (veri kayıplı kabul edilebilir).

> **Mülakat sorusu:** Backpressure neden imperative'de yok?
> Cevap: Imperative'de producer = caller, consumer = callee, sync — caller bekler. Backpressure async'de gerekir çünkü producer/consumer farklı hız ve farklı thread'de.

---

## 7. `subscribeOn` vs `publishOn`

```java
flux
  .map(...)              // calling thread
  .subscribeOn(io)       // upstream io scheduler'da çalışır
  .map(...)              // io scheduler
  .publishOn(parallel)   // sonraki adımdan itibaren parallel scheduler
  .map(...)              // parallel
```

- `subscribeOn` — upstream **kaynağın** çalıştığı scheduler. Yalnız bir kez etkili.
- `publishOn` — downstream'e **geçişi** belirler. Çoklu kullanılabilir.

> **Mülakat sorusu:** Hangi durumda `boundedElastic`?
> Cevap: `Schedulers.boundedElastic()` blocking-allowed thread pool. Kötü ama mecbur kaldığında (legacy blocking lib) reactor pipeline içinde wrapper olarak kullanılır.

---

## 8. Test — StepVerifier

```java
StepVerifier.create(service.byId(1L))
  .expectNextMatches(p -> p.id() == 1L)
  .verifyComplete();

StepVerifier.create(service.byId(99L))
  .expectError(ResourceNotFoundException.class)
  .verify();

// time-based testler için VirtualTimeScheduler
StepVerifier.withVirtualTime(() -> Flux.interval(Duration.ofSeconds(1)).take(3))
  .thenAwait(Duration.ofSeconds(3))
  .expectNextCount(3)
  .verifyComplete();
```

WebTestClient (integration):
```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
class IT {
  @Autowired WebTestClient client;
  @Test void it() {
    client.get().uri("/api/catalog/products").exchange()
          .expectStatus().isOk()
          .expectBodyList(ProductRow.class).hasSize(3);
  }
}
```

---

## 9. Yaygın Tuzaklar

| Pitfall | Açıklama | Çözüm |
|---|---|---|
| `.block()` production'da | Ana thread'i bloklar = WebFlux'ın faydası gider | Test'te OK, prod'da hayır |
| Blocking JDBC çağrısı reactor pipeline'da | Event loop'u bloklar = tüm sistem yavaşlar | `boundedElastic` veya R2DBC |
| MDC log context kayıp | reactor thread context propagate etmez | `Hooks.enableAutomaticContextPropagation()` (Reactor 3.5+) |
| `subscribe()` çift call | Pipeline iki kere yürütülür | Subscribe'ı framework'e bırak |
| Lazy bug | `Mono.fromCallable` yerine `Mono.just(eagerCall())` | Pipeline subscribe öncesi yürütülür |

---

## 10. Mülakat Cevapları — Hızlı Referans

**S: WebFlux ne zaman?**
**C:** Streaming endpoint (SSE/WS), reactive client zorunlu olduğunda (MongoDB reactive driver), backpressure kritik, çok sayıda concurrent idle connection. Pure CRUD'da Servlet + Loom yeterli.

**S: Mono vs CompletableFuture?**
**C:** İkisi de async. Mono Reactive Streams uyumlu (backpressure spec), `flatMap` + `zip` zengin operator set. CompletableFuture daha basit, JDK 8+ standard. Mono ile composition daha temiz; CF ile interop kolay.

**S: R2DBC ile transaction nasıl?**
**C:** `@Transactional` reactive context'te de çalışır (Spring Data R2DBC `R2dbcTransactionManager`). Bütün operasyonlar tek bir Mono/Flux pipeline'ında olmak zorunda — nested transaction veya cross-flux propagation imperative kadar trivial değil.

**S: WebFlux'ta Spring Security?**
**C:** `spring-boot-starter-security` reactive `ReactiveAuthenticationManager` + `WebFilter`. Imperative versiyondan farklı. JWT için `ReactiveJwtDecoder` + `BearerTokenAuthenticationConverter`.

**S: Performance testi yaptınız mı?**
**C:** Bu phase'de no — rakam claim etmek araştırmasız tehlikeli. Reactive avantajı düşük-CPU yüksek-I/O scenario. Pure JSON serialize CPU-bound; orada Servlet yarışır.

**S: WebFlux ile log nasıl trace edersin?**
**C:** Reactor 3.5+ `Hooks.enableAutomaticContextPropagation()` MDC'yi reactor signal'leri arasında propagate eder. Eski versiyonda manuel `contextWrite(Context.of(...))`.

**S: SSE vs polling?**
**C:** Polling client her N saniyede istek atar — gereksiz traffic + latency. SSE bağlantı bir kere açılır, server push eder — gerçek zamanlı + verimli.

---

## 11. Phase 10 Çıktıları

- **14. modül:** `catalog-stream-service` (port 8089)
- **WebFlux + R2DBC** — annotated controller, `ReactiveCrudRepository`
- **4 endpoint:** page, byId, search, **SSE stream**
- **Reuses productdb** read-only — schema sahipliği product-service'te
- **Gateway routes** `/api/catalog/**`; GET'ler JWT bypass
- **8 test pass** (1 smoke + 7 StepVerifier unit) — 14 modül `mvn clean verify` SUCCESS (~1dk 11s)
- **Türkçe notlar:** imperative vs reactive, Mono/Flux, lazy eval, R2DBC, SSE, backpressure, scheduler, StepVerifier, Loom karşılaştırması, mülakat Q&A
- **Tag:** `phase-10-complete`

---

## 12. Sıradaki — Phase 11

**Phase 11 — Performance + Production-Readiness:**
- Caching stratejileri (Caffeine + Redis layer)
- Connection pool tuning (Hikari + R2DBC pool)
- N+1 query audit + index review
- Idempotency-Key middleware (POST endpoint dedup)
- Rate limiting (Bucket4j veya Resilience4j)
- Graceful shutdown
- Liveness vs readiness probe ayrımı
