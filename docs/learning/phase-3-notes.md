# Faz 3 — Inter-Service Communication (Türkçe Öğrenme Notları)

> Faz 3'ün **mülakat hazırlığı** dokümantasyonu. Cart Service, OpenFeign, Resilience4j (CB+Retry+TimeLimiter), WireMock, snapshot pattern, Strategy via interface.

---

## 1. Genel yapı

`services/cart-service` (port 8083) — alışveriş sepeti, in-memory store. Gateway header'ına güveniyor (`X-User-Id`, `X-User-Role`). Sepete ürün eklemek için **Feign** ile product-service'i çağırıyor; çağrı **Resilience4j** (CircuitBreaker + Retry + TimeLimiter) ile sarılı; downstream çökerse fallback `ProductUnavailableException` (503) fırlatıyor.

**Endpoint'ler** (`/api/cart`, hepsi authenticated):
```
GET    /api/cart                     — current user's cart
POST   /api/cart/items               — {productId, quantity}
PATCH  /api/cart/items/{productId}   — {quantity}  (qty=0 = remove)
DELETE /api/cart/items/{productId}
DELETE /api/cart                      — clear
```

---

## 2. OpenFeign nedir? `RestTemplate` / `WebClient` farkları

**Feign** = declarative HTTP client. Interface yazarsın, Spring proxy üretir.

```java
@FeignClient(name = "product-service", fallbackFactory = ProductClientFallbackFactory.class)
public interface ProductClient {
  @GetMapping("/api/products/{id}")
  ApiResponse<ProductSnapshot> getById(@PathVariable("id") Long id);
}
```

**Avantajları:**
- ✅ İmperative kod yok, sadece interface
- ✅ Spring Cloud LoadBalancer ile entegre — `lb://product-service` URI Eureka discovery + client-side balancing
- ✅ Resilience4j ile entegrasyon kolay
- ✅ Aynı annotation'ları (`@GetMapping`, `@PathVariable`) Spring MVC'den biliyorsun

**Karşılaştırma:**

| | **RestTemplate** | **WebClient** | **Feign** |
|---|---|---|---|
| Stil | Imperative (blocking) | Reactive (non-blocking) | Declarative |
| Spring desteği | ✅ ama deprecated for new code | ✅ önerilen reactive | ✅ Spring Cloud OpenFeign |
| Service discovery | Manuel | Manuel | Otomatik (`lb://`) |
| LoadBalancing | RibbonRestTemplate (legacy) | Spring Cloud LB | Otomatik |
| Boilerplate | Çok | Orta | Az |
| Async | ❌ | ✅ | Optional (return `CompletableFuture`) |

**Mülakat sorusu:** *Niye Feign?*
> Service-to-service call'larda yazdığın kod sadece "hangi endpoint'i hangi tipler ile çağırıyorum" — Feign tam bunu declare eder. URL construction, deserialization, retry hooks otomatik. RestTemplate verbose, WebClient reactive eklemediğimiz yerde overkill. **Feign sweet spot for sync inter-service calls.**

---

## 3. Resilience4j chain order — niye `TimeLimiter → Retry → CircuitBreaker → Feign`?

Spring Cloud OpenFeign + Resilience4j integration her Feign metodunu şu sırayla wrap eder (dıştan içe):

```
   ┌──────────────────────────────────────────────────────────┐
   │  TimeLimiter (2s)                                         │
   │   ┌───────────────────────────────────────────────────┐   │
   │   │  Retry (max 3, exponential backoff)               │   │
   │   │   ┌──────────────────────────────────────────┐    │   │
   │   │   │  CircuitBreaker (50% over 10 calls)      │    │   │
   │   │   │   ┌─────────────────────────────┐        │    │   │
   │   │   │   │  Feign HTTP call            │        │    │   │
   │   │   │   └─────────────────────────────┘        │    │   │
   │   │   └──────────────────────────────────────────┘    │   │
   │   └───────────────────────────────────────────────────┘   │
   └──────────────────────────────────────────────────────────┘
```

**Bir slow call'un yolculuğu:**
1. TimeLimiter saatini başlatır (2s)
2. Retry loop'a girer, ilk denemeyi başlatır
3. CB closed → call izinli, Feign HTTP çağrısı yapılır
4. 2s geçince TimeLimiter cancel eder → `TimeoutException`
5. Retry: bu exception retryable mı? Eğer evet, 200ms bekle, deneme 2 başlat (yine CB → Feign)
6. 3 deneme de fail ederse Retry final exception'ı upstream'e atar
7. CB bunu **failure** olarak kaydeder
8. 5 minimum call sonrası, ≥%50 fail ratio → CB **closed → open**
9. Open state'te CB Feign'a uğramaz; doğrudan `CallNotPermittedException`
10. Spring Cloud OpenFeign Integration: CB exception'ında **fallback factory**'yi çağırır
11. Fallback `ProductUnavailableException` fırlatır
12. `GlobalExceptionHandler` bunu `ErrorResponse` 503'e çevirir

**Mülakat sorusu:** *Decorator order'ı niye böyle?*
> İçten dışa: CB her bir denemenin başarı/başarısızlığını sayar. Retry her denemeyi tekrarlar (bu yüzden Retry CB'nin dışında). TimeLimiter en dışta çünkü tüm retry zincirinin total süresini limitlemek istemeyiz, **her bir** denemenin süresini limitlemek isteriz — yanlış. **Doğru:** TimeLimiter Spring Cloud CircuitBreaker integration'ında her **deneme** için ayrı saat tutar. Yani 3 retry × 2s = 6s worst-case toplam, ama her individual call 2s'den fazla beklemez.

---

## 4. Circuit Breaker state machine

```
    closed ────[%50+ fail in window]──→ open
       ▲                                  │
       │                                  │
       │                          [10s wait]
       │                                  │
       │                                  ▼
       └──[3 trial calls succeed]── half-open
                                          │
                                  [trial fails]
                                          │
                                          ▼
                                        open
```

**State'ler:**
- **closed** — normal operation, her call izinli, CB sayar
- **open** — failure threshold aşıldı, **call yapılmaz**, hızlı fail. Downstream nefes alabilir.
- **half-open** — `wait-duration-in-open-state` (10s) sonrası deneme. `permitted-number-of-calls-in-half-open-state` (3) tane call'a izin verir, hepsi başarılıysa closed, biri fail ederse open.

**Sliding window türleri:**
- **COUNT_BASED** (bizim) — son 10 call'a bakar
- **TIME_BASED** — son 10 saniye

**Niye COUNT_BASED?** Düşük trafikte time-based çalışmaz (hiç call gelmediği zaman). Count-based daha öngörülebilir.

**Bizim config:**
```yaml
sliding-window-size: 10
minimum-number-of-calls: 5  # 5 call yoksa karar verme (cold start defense)
failure-rate-threshold: 50
wait-duration-in-open-state: 10s
permitted-number-of-calls-in-half-open-state: 3
```

**Mülakat sorusu:** *`minimum-number-of-calls` niye?*
> Cold start sırasında ilk birkaç call fail olabilir (DNS not warmed, etc.). Eğer 1. call'a bakarsak %100 fail → CB hemen açılır → hizmet hiç ısınmadan kapanır. Minimum 5 = "yeterli sample yokken karar verme."

---

## 5. Retry exponential backoff + idempotency

```yaml
retry:
  instances:
    productClient:
      max-attempts: 3
      wait-duration: 200ms
      exponential-backoff-multiplier: 2
      retry-exceptions:
        - feign.RetryableException   # connection refused, IOException
        - java.io.IOException
```

İlk deneme fail → 200ms bekle → 2. deneme → fail → 400ms bekle → 3. deneme → fail → exception upstream.

**Niye exponential?** Linear (her seferinde 200ms) bir bursta downstream'i daha çok döver. Exponential (200, 400, 800) downstream'e iyileşme şansı verir.

**`retry-exceptions` neden specific?** Tüm exception'ları retry'lamak yanlış:
- `ResourceNotFoundException` (404) — retry anlamlı değil, ürün yoksa yok
- `ValidationException` (400) — retry, kullanıcı hatasını tekrar çözmez
- `IOException` / `RetryableException` (network blip) — **bu retry adayı**

**Idempotency uyarısı:** GET, PUT, DELETE idempotent → güvenli retry. POST genelde değil → retry "double-charge" yapabilir. Bizim Feign GET only, sorun yok.

**Mülakat sorusu:** *POST'u retry yapsam ne olur?*
> Idempotency anahtarı (Idempotency-Key header) yoksa: aynı POST iki kere işlenebilir. Örnek: payment POST, ilk request server'da işlenmiş ama response client'a ulaşmamış → client retry → ikinci payment! Çözümler: (a) Idempotency-Key header + server'da dedupe, (b) sadece "safely retryable" POST'ları retry'la (örn. `If-None-Match` ile koşullu).

---

## 6. TimeLimiter vs server-side timeout

**Server-side timeout** — server N saniye sonra connection'ı kapatır. Client cevabı bekler, network'ten EOF gelir.

**TimeLimiter (client-side)** — client kendi timer'ı, N saniye sonra request'i cancel eder.

İkisi birden niye?
- ✅ **Defense in depth** — server timeout vermeyi unutursa client'ı koruruz
- ✅ **Predictable client behavior** — server'ın config'inden bağımsız
- ✅ **Resource isolation** — slow server bizim thread'leri tutmasın

**Mülakat sorusu:** *Sadece HTTP client timeout (Feign default `read-timeout`) yetmez mi?*
> Client read-timeout pasif: TCP'den X saniye veri gelmezse fail. Ama server slowly streaming (1 byte/sec) ise read-timeout asla atmaz, sonsuza kadar takılır. TimeLimiter active: tüm call için duvar saati, garantili cancel.

---

## 7. Bulkhead pattern (sözel — implement etmedik)

Resilience4j Bulkhead = **kaynak izolasyonu**.

**Senaryo:** Cart-service hem product-service hem inventory-service çağırıyor. Eğer product-service slow ise, cart-service'in tüm thread'leri product-service çağrısında takılı kalır → inventory-service çağrıları için thread yok → cart-service çöker.

**Çözüm:** Her downstream için ayrı thread pool (Bulkhead).

```yaml
resilience4j.bulkhead:
  instances:
    productClient:
      max-concurrent-calls: 10
      max-wait-duration: 500ms
```

10 paralel productClient çağrısı; 11. çağrı 500ms bekler, sonra fail. inventory-service'in havuzu ayrı, etkilenmez.

**İki tip:**
- **SemaphoreBulkhead** (default, light): counter, lock-free
- **ThreadPoolBulkhead**: ayrı thread pool, async

**Niye implement etmedik?** Phase 3'te tek downstream var (product-service). Multi-downstream Phase 5'te (Order → Inventory + Payment). O zaman Bulkhead anlam kazanır.

**Mülakat:** *CB vs Bulkhead?*
> CB = "downstream sağlıksız, aramayı dur" (fail fast). Bulkhead = "downstream'e olan call sayımı sınırla" (resource isolation). Birbirini tamamlar: CB latency/error patikası, Bulkhead concurrency/saturation patikası.

---

## 8. Service mesh alternatifi — Istio / Linkerd

**Library-based resilience (bizim):**
- Resilience4j Spring uygulaması içinde
- Her servis kendi config'i, kendi koduyla
- Pro: kontrol elinde
- Con: her dilde/framework'te yeniden yapılandırma; consistent değil

**Service mesh (Istio):**
- Sidecar proxy (Envoy) her pod'a iliştirilir
- Resilience policy mesh-level (CRD ile YAML)
- Code change gerekmez
- mTLS, observability, traffic shifting (canary, A/B) bonus

**Mülakat:** *Service mesh ile Resilience4j birlikte kullanır mısın?*
> İhtiyaca göre. Mesh = baseline (mTLS, basic retry/timeout, traffic management). Library = business-aware policies (örn. "stock check call'da 1 retry, payment'ta 0 retry"). Hibrit yaygın.

---

## 9. Snapshot pattern — niye cart'a price + name kopyalıyoruz?

```java
public record CartItem(
    Long productId,
    String productName,         // snapshotted at add time
    BigDecimal priceAmount,     // snapshotted
    String priceCurrency,
    int quantity) {}
```

**Senaryo:** User Wireless Headphones'u 1799 TL'de cart'a ekledi. 10 dakika sonra admin fiyatı 1999 TL'ye çıkardı.

- **Snapshot YOK:** User cart'ı görüyor → product-service'ten fresh fiyat çekilir → 1999 TL → user şaşırır → muhtemelen vazgeçer
- **Snapshot VAR:** Cart hala 1799 gösterir → user mutlu → checkout'ta re-validate (Phase 5) → fiyat değiştiyse user'a bildir, onay iste

**Mülakat:** *Snapshot vs reference data?*
> Snapshot = "time-of-action capture" (cart, order, invoice). Reference = "live link" (user info, product description display). Para hesaplamalarında **always snapshot**. Ödeme zamanında "10 dakika önceki fiyat hala valid mi" diye re-validate ederek finalize.

**Implementation note:** snapshot ne zaman invalidate olur?
- Phase 5 checkout'ta product-service'ten current price çekilir
- Eğer farklıysa user'a "fiyat değişti, onay" diyalog
- Onaylarsa yeni snapshot, cart güncellenir
- Reddederse cart eski fiyatla, ama checkout'a izin verilmez

---

## 10. Strategy pattern via `CartStore`

```java
public interface CartStore {
  Cart get(Long userId);
  Cart save(Cart cart);
  void clear(Long userId);
}

@Component
public class InMemoryCartStore implements CartStore { ... }
```

Phase 3'te tek implementation: `InMemoryCartStore` (ConcurrentHashMap).

Phase 4 eklenecek: `RedisCartStore implements CartStore`.

**Switching:** `@Component` etiketini `RedisCartStore`'a taşı, `InMemoryCartStore`'u sil (veya `@Profile("dev")` ile profile-based seç). **CartService değişmez.**

**Mülakat:** *Open/Closed principle nasıl uygulandı?*
> CartService **closed for modification** (interface'e bağlı, implementation bilmiyor). CartStore **open for extension** (yeni impl eklenebilir). Phase 4'te Redis eklenince service code dokunulmaz, yeni implementation, end of story.

---

## 11. Immutable records — concurrency safety

`Cart` ve `CartItem` Java records — final fields, copy-on-write.

```java
public Cart upsertItem(CartItem incoming) {
  // ... build new list
  return new Cart(userId, List.copyOf(next), Instant.now());  // YENİ Cart
}
```

**Niye?**
- ✅ **Thread safety** — paylaşılan referans değişmez, race condition yok
- ✅ **Predictable** — `cart.items()` sonucunu değiştiren `cart.upsertItem()` aynı `cart` objesini etkilemez
- ✅ **Functional style** — pipeline'da composable
- ❌ Memory: her mutation yeni allocation (modern JVM bunu çok iyi handle eder)

**Mülakat:** *Mutable Cart yapsak ne fark eder?*
> Concurrency için synchronization (synchronized, locks) gerek. Race conditions kolay. State change tracking zor. Functional style'a uymaz. Modern Java + JIT yeni allocation overhead'ı önemli değil; immutable kazançlar büyük.

---

## 12. WireMock testing

```java
@SpringBootTest @EnableWireMock
@TestPropertySource(properties = {
  "spring.cloud.openfeign.client.config.product-service.url=${wiremock.server.baseUrl}",
  "spring.cloud.openfeign.circuitbreaker.enabled=true",
  // tighten Resilience4j for fast tests
})
class CartFlowIntegrationTest {
  @InjectWireMock WireMockServer wm;
  // ...
}
```

**WireMock vs alternatifler:**

| | **WireMock** (bizim) | **Testcontainers + real product-service** | **Mockito @MockBean ProductClient** |
|---|---|---|---|
| Real HTTP | ✅ | ✅ | ❌ |
| CB/Retry/TimeLimiter test | ✅ (gerçek davranış) | ✅ | ❌ (mock direkt cevap) |
| Setup complexity | Düşük | Yüksek | Çok düşük |
| CI hız | Hızlı | Yavaş | En hızlı |

WireMock = "fake HTTP server", contract test simulation. Resilience4j test'i için ideal çünkü gerçek HTTP layer'ı geçer (timeout, IOException, 5xx kodları gerçek olur).

**Test config tightening:**
```yaml
sliding-window-size: 2          # 2 fail = CB opens (default 10)
minimum-number-of-calls: 2
max-attempts: 2                 # 2 retry (default 3)
wait-duration: 10ms             # fast retry (default 200ms)
```
Test 2-3 saniyede tamamlanır, real config production-grade kalır.

**Mülakat:** *Resilience pattern'larını nasıl test edersin?*
> WireMock ile contract simulation. Tight config ile (sliding window 2, retry 2, fast wait) test fast. Senaryolar: success path, 5xx triggers retry, retries exhausted triggers CB, CB opens triggers fallback, slow response triggers TimeLimiter. Bu hepsi production behavior'unu kanıtlar.

---

## 13. Mülakatta Faz 3 hikayesi

> Cart Service'i in-memory store ile başlattım, `CartStore` interface'iyle Strategy pattern uyguladım — Phase 4'te Redis'e geçiş code-side dokunmadan single-line swap. OpenFeign declarative client `lb://product-service` URI'siyle Eureka discovery + client-side load balancer'ı otomatik kullanıyor; her Feign call **TimeLimiter (2s) → Retry (3x exponential backoff) → CircuitBreaker (50%/10 sliding window)** chain'inde sarılı. CB open olduğunda fallback factory `ProductUnavailableException` fırlatıyor — `BusinessException` kalıtımıyla `ErrorCode.SERVICE_UNAVAILABLE` (HTTP 503) ve global exception handler ile ortak `ErrorResponse` shape'inde döner. Cart `CartItem` record'larında **price/name snapshot at add-time** ile fiyat değişimine karşı stable; checkout'ta re-validate (Phase 5). Cart ve CartItem records immutable, mutation copy-on-write — concurrency safe. WireMock + MockMvc integration test ile gerçek HTTP davranışında 5xx → retry → CB opens → fallback → 503 zincirini kanıtladım.

---

## 14. Mülakat soruları (kısa liste)

1. OpenFeign vs RestTemplate vs WebClient — niye Feign?
2. Spring Cloud LoadBalancer + Eureka discovery — `lb://` URI nasıl çalışır?
3. Resilience4j chain order: niye TimeLimiter dışta, CB içte?
4. CircuitBreaker state machine — closed/open/half-open
5. Sliding window count vs time — niye count seçtin?
6. `minimum-number-of-calls` neden cold start defense?
7. Retry exponential backoff + jitter — niye linear değil?
8. Idempotency niye retry için kritik?
9. TimeLimiter vs HTTP client read-timeout farkı?
10. Bulkhead vs CB — ne zaman hangisi?
11. Service mesh ile library-based resilience trade-off?
12. Snapshot vs reference data in cart/order — niye snapshot?
13. Strategy pattern (`CartStore` interface) — Phase 4'e nasıl yardım eder?
14. Immutable records vs mutable classes — concurrency?
15. WireMock vs Testcontainer vs Mockito — Resilience4j testleri için?

---

## 15. Faz 3'te yazılan dosyaların haritası

```
services/cart-service/
├── pom.xml                                                # Feign + R4J + WireMock test deps
├── src/main/
│   ├── java/com/backendguru/cartservice/
│   │   ├── CartServiceApplication.java                    # @EnableFeignClients
│   │   ├── auth/
│   │   │   ├── HeaderAuthenticationFilter.java            # X-User-* trust (Phase 2 pattern)
│   │   │   └── SecurityConfig.java                        # all /api/cart authenticated
│   │   ├── cart/
│   │   │   ├── Cart.java, CartItem.java                   # immutable records
│   │   │   ├── CartStore.java                             # Strategy interface
│   │   │   ├── InMemoryCartStore.java                     # ConcurrentHashMap impl
│   │   │   ├── CartService.java                           # Feign+R4J product validation
│   │   │   ├── CartController.java                        # /api/cart endpoints
│   │   │   └── dto/{Cart, CartItem}Response, AddItem, UpdateItem
│   │   ├── product/
│   │   │   ├── ProductClient.java                         # @FeignClient
│   │   │   ├── ProductClientFallbackFactory.java
│   │   │   └── dto/ProductSnapshot.java                   # minimal, decoupled
│   │   ├── exception/
│   │   │   ├── ProductUnavailableException.java           # SERVICE_UNAVAILABLE 503
│   │   │   └── GlobalExceptionHandler.java
│   │   └── config/OpenApiConfig.java                      # Swagger bearer
│   └── resources/application.yml + logback-spring.xml
└── src/test/java/com/backendguru/cartservice/
    ├── CartServiceApplicationTests.java                   # smoke (mocked Feign)
    ├── cart/CartTest.java                                 # 10 record tests
    ├── cart/InMemoryCartStoreTest.java                    # 4 store tests
    ├── cart/CartServiceTest.java                          # 12 unit tests (Mockito)
    └── CartFlowIntegrationTest.java                       # 7 WireMock E2E tests

infrastructure/config-server/src/main/resources/configs/
├── cart-service.yml                                       # port 8083 + R4J config
└── cart-service-dev.yml                                   # log levels

infrastructure/config-server/src/main/resources/configs/api-gateway.yml — added cart-service route
```

**Toplam test:** 34 (10 + 4 + 12 + 7 + 1)
