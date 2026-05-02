# Phase 5 — Order + Inventory + Payment (Saga, Iyzico mock)

> Hedef: Üç servis (inventory, payment, order) ekleyip, **Saga orchestration pattern** ile dağıtık işlemi (distributed transaction) ele almak. Her adım kendi DB'sine yazıyor; tek bir XA/2PC yok — başarısızlıkta **compensation** çalışıyor.

---

## 1. Servis Mimarisi

```
                ┌────────────┐
   POST /api/orders  ─────▶ │  Order     │  (8086)  Saga orchestrator
                            │  Service   │
                            └─┬─┬─┬─────┘
                  Feign       │ │ │       Feign
              ┌───────────────┘ │ └───────────────┐
              ▼                 ▼                 ▼
       ┌───────────┐    ┌──────────────┐   ┌─────────────┐
       │  Cart     │    │  Inventory   │   │  Payment    │
       │  Service  │    │  Service     │   │  Service    │
       │  (Redis)  │    │  (PostgreSQL)│   │ (PostgreSQL)│
       └───────────┘    └──────────────┘   └─────────────┘
         8083              8084                8085
```

Her servisin **kendi veritabanı** var (database-per-service ilkesi). Aralarında doğrudan DB erişimi YOK — sadece HTTP (Feign) üzerinden.

---

## 2. Saga Pattern — Orchestration vs Choreography

### Orchestration (bizim seçimimiz)
- Bir **merkezi orchestrator** (Order Service) tüm adımları sırayla çağırır.
- Hata olursa orchestrator **compensation**'ları (telafi adımları) tetikler.
- **Avantaj:** akış kodda tek yerde, debug kolay.
- **Dezavantaj:** orchestrator tek nokta sorumluluğu (single point of coordination).

### Choreography (alternatif — Phase 7'de göreceğiz)
- Servisler birbirini **event** ile dinler (Kafka).
- Merkezi koordinatör yok; her servis "X olduğunda Y yap" diye reaktif çalışır.
- **Avantaj:** decoupled, scale eder.
- **Dezavantaj:** akışı izlemek zor, distributed tracing şart.

> **Mülakat sorusu:** "Saga orchestration ile choreography arasındaki fark nedir?"
> Cevap: orchestration'da merkezi bir akış kontrolcüsü vardır (komutlar gönderir, durumu izler), choreography'de servisler eventleri dinler ve kendi başına reaksiyon verir.

---

## 3. Order Saga — 7 Adım

```java
public OrderResponse placeOrder(Long userId, PlaceOrderRequest req) {
  // 1. Cart'ı çek
  CartSnapshot cart = cartClient.getCart(...);
  if (cart.isEmpty()) throw new ValidationException("Cart is empty");

  // 2. Order'ı PENDING olarak persist et
  Order order = persistPendingOrder(...);

  List<Long> reservationIds = new ArrayList<>();
  Long paymentId = null;

  try {
    // 3. Her item için inventory rezerve et
    for (item : order.items) {
      reserve(item);  // FAIL → release(prev) + cancel(order) + throw
      reservationIds.add(reservationId);
    }

    // 4. Payment'ı charge et
    paymentId = paymentClient.charge(...);  // FAIL → release(all) + cancel + throw

    // 5. Reservation'ları COMMIT et (stok düş)
    for (id : reservationIds) inventoryClient.commit(id);
    // FAIL → refund(payment) + release(all) + cancel + throw  ← KRİTİK

    // 6. Order'ı CONFIRMED yap
    order.setStatus(CONFIRMED);

    // 7. Cart'ı temizle (best-effort: hata olsa bile devam)
    cartClient.clearCart(userId);
  } catch (...) { /* compensations */ }
}
```

### Compensation Tablosu

| Hata Adımı | Yapılacak Compensation |
|---|---|
| Step 3 (reserve fail) | önceki rezervasyonları release et, order'ı CANCELLED yap |
| Step 4 (payment fail) | tüm rezervasyonları release et, order'ı CANCELLED yap |
| Step 5 (commit fail) | **refund** + release + CANCELLED |
| Step 6+ (beklenmeyen) | refund + release + CANCELLED |

**Önemli:** Step 5 başarısız olduğunda payment ZATEN authorize edilmiş — para çekilmiş. Bu yüzden refund şart, yoksa müşteri parası kaybolur.

---

## 4. Best-Effort vs Catastrophic Failures (Feign Fallback)

Her Feign client'ın `FallbackFactory`'si var — circuit breaker açıldığında ne olacağı:

| İşlem | Fallback Davranışı |
|---|---|
| `cartClient.getCart()` | **throw SagaException** → saga abort eder, çünkü cart olmadan order olamaz |
| `cartClient.clearCart()` | **log + ignore** → cart temizlenmemesi order başarısını bozmaz, manuel cleanup kalır |
| `inventoryClient.reserve()` | **throw SagaException** → catastrophic |
| `inventoryClient.commit()` | **throw SagaException** → catastrophic, refund tetiklenir |
| `inventoryClient.release()` | **log + ignore** → compensation, sonsuz döngüye girmesin |
| `paymentClient.charge()` | **throw SagaException** → catastrophic |
| `paymentClient.refund()` | **log + ignore** → compensation |

> **Mülakat sorusu:** "Compensation adımı kendisi başarısız olursa ne yaparsın?"
> Cevap: Logla, alert at, manuel intervention bayrak (failure_reason kolonu) bırak. Compensation'da retry mantıklı ama sonsuza kadar değil — bir noktada ops ekibi devreye girmeli (saga state machine + dead-letter queue).

---

## 5. Inventory Service — Reservation Lifecycle

```
HELD ─── commit() ──▶ COMMITTED  (stok kalıcı düşer)
  │
  └──── release() ──▶ RELEASED   (stok geri gelir)
```

- **Reservation** entity'si stok'a ATOMIK olarak yazılır (`@Version` ile optimistic lock).
- `reserve()` çağrısı: `available_qty -= requested_qty` + reservation row insert.
- `commit()`: status = COMMITTED (stok zaten düşmüştü; sadece "kalıcı" işareti).
- `release()`: `available_qty += reserved_qty` + status = RELEASED.

> **Mülakat sorusu:** "İki istek aynı anda son ürünü almak isterse?"
> Cevap: `@Version` (Hibernate optimistic lock) — biri kazanır, diğeri `OptimisticLockException` alır → 409 Conflict döneriz. Yüksek concurrency'de pessimistic lock veya Redis-based distributed lock düşünülebilir.

---

## 6. Payment Service — Iyzico Mock

Iyzico Türkiye'nin en yaygın PSP'lerinden. API'sini taklit eden bir **deterministic mock** yazdık:

| Kart No | Davranış |
|---|---|
| `4111-1111-1111-1115` | her zaman **decline** (PaymentDeclinedException) |
| Diğer geçerli formatlı kartlar | her zaman **success** |

```java
if (last4 == "1115") {
  payment.setStatus(FAILED);
  payment.setFailureReason("CARD_DECLINED");
  paymentRepo.save(payment);     // audit trail
  throw new PaymentDeclinedException(...);
}
```

> **Önemli not:** `PaymentDeclinedException` `@Transactional` içinde fırlatıldığında bütün transaction rollback olur — FAILED row'u kaybolur. Bu yüzden audit ledger için ayrı bir `REQUIRES_NEW` propagation lazım. Şimdilik bu basit halde bıraktık (Phase 11'de optimize edilecek).

---

## 7. Header-Trust Security (Yine!)

Order Service de cart/product gibi `X-User-Id` ve `X-User-Role` header'larına güveniyor.

- **Gateway** JWT'yi doğrular, payload'dan userId/role'ü alır, header olarak iletir.
- **Downstream servis** JWT'yi yeniden parse etmez — sadece header'a güvenir.
- **Kritik:** gateway dışından gelen istekler için `X-User-*` header'ları gateway'de zorla strip edilir (forge'a karşı).

```java
// HeaderAuthenticationFilter
String userId = req.getHeader("X-User-Id");
String role = req.getHeader("X-User-Role");
auth = new UsernamePasswordAuthenticationToken(
    Long.valueOf(userId),
    null,
    List.of(new SimpleGrantedAuthority("ROLE_" + role)));
```

---

## 8. Resilience4j Profilleri (Per-Feign-Client)

`order-service.yml`:

```yaml
resilience4j:
  circuitbreaker:
    instances:
      cart-service: { sliding-window-size: 10, failure-rate-threshold: 50 }
      inventory-service: { ... }
      payment-service: { ... }
  retry:
    instances:
      cart-service:     { max-attempts: 3 }   # idempotent GET — retry safe
      inventory-service:{ max-attempts: 2 }   # reserve değil ama biz idempotency-key bekliyoruz
      payment-service:  { max-attempts: 1 }   # ⚠️ retry YASAK — double charge riski
```

> **Mülakat sorusu:** "Payment'ı neden retry etmiyorsun?"
> Cevap: Payment idempotent değil. Aynı isteği iki kere göndermek **iki kere para çekmek** anlamına gelebilir. Idempotency-key olmadan retry tehlikeli. Iyzico real-world'de idempotency-key destekliyor; mock'ta basitleştirdik.

---

## 9. Test Stratejisi

### Unit Test (Mockito) — `OrderServiceTest`

5 senaryo:
1. **Happy path** — saga sonuna kadar gider, CONFIRMED.
2. **Empty cart** — ValidationException, hiç save/charge çağrılmaz.
3. **Reserve fail (2nd item)** — ilk reservation release edilir, CANCELLED.
4. **Payment fail** — release + CANCELLED, refund **çağrılmaz** (henüz para çekilmedi).
5. **Commit fail** — refund + release + CANCELLED (en kritik path).

### Smoke Test — `OrderServiceApplicationTests`
Spring context boot ediyor mu (Eureka/Config disabled, MockBean Feign clients).

> **Mülakat sorusu:** "Saga'yı end-to-end nasıl test edersin?"
> Cevap: Testcontainers ile her servisin DB'sini ayağa kaldır, WireMock ile dış bağımlılıkları stub'la, real Feign + Resilience4j calls yap. Çok pahalı bir test — bu yüzden CI'da `verify` aşamasında selektif çalıştırılır.

---

## 10. Gateway Routing

`api-gateway.yml`:

```yaml
- id: order-service
  uri: lb://order-service
  predicates:
    - Path=/api/orders/**
```

`lb://` prefix → Spring Cloud LoadBalancer + Eureka discovery. Servis yeniden başlatıldığında host:port'u yeniden öğrenir.

---

## Mülakat Cevapları — Hızlı Referans

**S: Distributed transaction'ı nasıl ele alıyorsun?**
**C:** Saga orchestration pattern. Her servis kendi local transaction'ını commit eder. Başarısızlıkta orchestrator önceki adımlar için compensation komutu gönderir. ACID değil, **eventual consistency** — kabul edilebilir bir trade-off.

**S: 2PC neden tercih etmedin?**
**C:** 2PC bütün servislerin aynı XA-aware resource manager'ı paylaşmasını gerektirir, blocking'tir, network kesilmesinde kilitlenir. Microservices'te pratik değil. Saga + idempotency anahtar-tabanlı işlemler daha sağlam.

**S: Payment yaptıktan sonra commit fail olursa müşteri parasını nasıl alır?**
**C:** Saga'nın en kritik branch'ı. `paymentClient.refund(paymentId)` çağrılır. Refund kendisi de fallback'e düşerse log + alert + ops müdahale (failure_reason kolonunda iz bırakırız).

**S: Stok yetersiz olduğunda order ne durumda kalır?**
**C:** PENDING → CANCELLED. failure_reason kolonunda "Reservation failed: INSUFFICIENT_STOCK" yazar. Frontend bunu kullanıcıya gösterir.

**S: İki müşteri aynı anda son ürünü almak isterse?**
**C:** Inventory service'in `inventory_items` tablosunda `@Version` var — optimistic lock. Biri commit eder, diğeri OptimisticLockException → 409. Bu order Saga'sında reserve fail olarak yansır → CANCELLED + release.

**S: Order'ın status state machine'i nedir?**
**C:** `PENDING → CONFIRMED` (happy) ya da `PENDING → CANCELLED` (fail). Geri gidiş yok; bir order CANCELLED olduktan sonra yeni bir order açılır.

**S: Idempotency'i nasıl ele alıyorsun?**
**C:** Şu anki MVP'de yok. Production'da `Idempotency-Key` header'ı + Redis-cache'lenmiş response gönderirdim (Stripe/Iyzico pattern). Phase 11'de eklenecek.

---

## Phase 5 Çıktıları

- **3 yeni servis** (inventory, payment, order) — toplam 10 modül
- **Saga orchestration** çalışıyor (5 unit test ile kanıtlı)
- **Iyzico mock** ile deterministic decline (kart `4111-...-1115`)
- **Per-feign-client** Resilience4j config (retry, CB, timelimiter ayrı)
- **`docker/postgres/init.sql`** ile 4 ayrı DB + user (database-per-service)
- **API Gateway** `/api/orders/**` route'u eklendi
- **Tag:** `phase-5-complete`

---

## Sıradaki Phase

**Phase 6 — Notification Service (RabbitMQ, async messaging):**
Order CONFIRMED olduğunda email/SMS notification gönder (asenkron, fire-and-forget). RabbitMQ exchange + queue + DLQ pattern. Burada **at-least-once delivery** ve **idempotent consumer** kavramları girecek.
