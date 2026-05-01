# Faz 4 — Redis Cart Backend (Türkçe Öğrenme Notları)

> Faz 4'ün **mülakat hazırlığı** dokümantasyonu. Strategy via Profile, Lettuce vs Jedis, Jackson record serialization, TTL, Testcontainers @DynamicPropertySource.

---

## 1. Genel yapı — Phase 3'ten ne değişti?

Phase 3'te `CartStore` interface + `InMemoryCartStore` (ConcurrentHashMap) yapmıştık. Phase 4'te:

```
@Component
@Profile("test")
public class InMemoryCartStore implements CartStore { ... }   // testlerde

@Component
@Profile("!test")
public class RedisCartStore implements CartStore { ... }       // dev/docker/prod
```

`CartService` ve `CartController` **sıfır değişiklik**. Strategy pattern'in vaadi gerçekleşti: implementation swap, business logic dokunulmadı.

**Açıklama:**
- Phase 3 mevcut testleri (`CartFlowIntegrationTest`, `CartServiceApplicationTests`) `@ActiveProfiles("test")` annotation aldı → InMemory kullanıyorlar, Redis gerektirmezler
- Yeni `RedisCartStoreTest` Testcontainers ile gerçek Redis 7-alpine başlatıyor, RedisCartStore'un kendisini test ediyor
- Production'da default profile = Redis aktif

**Mülakat sorusu:** *Open/Closed principle gerçek hayatta nasıl çalışır?*
> Bizim örnek: Phase 3'te `CartService` Strategy interface'ine yazıldı (closed for modification). Phase 4'te yeni implementation eklendi (open for extension). Service code dokunulmadı, sadece composition değişti — `@Profile` ile bean wiring. **3 satır kod değişti, davranış tamamen yeni.**

---

## 2. Lettuce vs Jedis — niye Lettuce?

| | **Lettuce** (bizim) | **Jedis** |
|---|---|---|
| Foundation | Netty (non-blocking) | Plain Java sockets |
| Thread safety | ✅ One connection, many threads | ❌ Connection per thread (pool gerek) |
| Reactive support | ✅ Mono/Flux native | ❌ Sync only |
| Spring Boot default | ✅ since 2.0 | Optional |
| Connection pooling | Implicit (Netty) | Explicit (`JedisPool`) |

**Niye Lettuce?** Çoğu use case için tek connection yeterli, thread-safe; pool yönetimi yok. Reactive ekosisteme geçiş kolay (Phase 9 reactive layer).

**Bizim config:**
```yaml
spring.data.redis:
  host: localhost
  port: 6380
  timeout: 2s
  lettuce.pool:
    max-active: 16
    max-idle: 8
    min-idle: 0
```

`max-active: 16` — Lettuce technically tek connection multiplexing yapıyor ama Spring Boot Commons Pool wrapper üzerinden çalıştırılıyor (Lettuce + commons-pool2). Production'da 16 yeterli; CPU bound olmayan yüklerde 32+ olabilir.

**Mülakat:** *Connection pool size nasıl seçilir?*
> Throughput / latency hesaplaması. Eğer ortalama Redis call 1ms ve max RPS 10K ise teorik gerek `10000 * 0.001 = 10 connection`. Pratikte 1.5x güvenlik faktörü → 16. `Caffeine cache` ile read'ları cache'lersek bu daha da düşer.

---

## 3. Jackson record serialization

`Cart` ve `CartItem` Java records. Jackson 2.16+ (Spring Boot 3.4 → 2.18) record'ları otomatik destekliyor — accessor methodları (`userId()`, `items()`) ile serialize ediliyor.

**Sorun: `Instant updatedAt`** field'ı default Jackson serializer ile epoch-second gibi serialize olur (kötü):
```json
{ "updatedAt": 1715000000.123456789 }
```

**Çözüm:** `JavaTimeModule` + `disable(WRITE_DATES_AS_TIMESTAMPS)`:
```java
ObjectMapper om = new ObjectMapper();
om.registerModule(new JavaTimeModule());
om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
```

Şimdi:
```json
{ "updatedAt": "2026-05-02T10:23:45.123Z" }
```

ISO-8601, human-readable, debugging kolay.

**`BigDecimal`** native: `"priceAmount": 5.00`. Float precision kaybı olmaz çünkü Jackson `BigDecimal`'ı string number gibi tutar.

**Mülakat:** *`BigDecimal`'ı JSON'a `5.00` mı `"5.00"` mı yazıyorsun?*
> Default: number `5.00`. Floating-point precision kaybı endişesi varsa `JsonFormat(shape = STRING)` ile string. Kabul edilebilir trade-off: number daha küçük payload, string parse-time guarantee. Para hesaplamalarında string + JS'de Big.js / decimal library yaygın.

---

## 4. Redis key naming + TTL stratejisi

```java
private static final String KEY_PREFIX = "cart:";
private static final Duration TTL = Duration.ofDays(30);

public Cart save(Cart cart) {
  cartRedisTemplate.opsForValue().set(key(cart.userId()), cart, TTL);
  return cart;
}
```

**Key format:** `cart:<userId>` — pattern `<entity>:<id>` Redis convention.

**Niye TTL?**
- ✅ Inactive user'ın cart'ı sonsuza kalmaz → memory bound
- ✅ Cart abandonment standardı: 30 gün sonra cleanup
- ✅ TTL her `save`'de yenilenir → user aktif olduğu sürece cart yaşar

**Niye TTL yenileniyor (LRU değil)?**
- LRU = Last-Recently-Used (Redis'te global maxmemory policy)
- TTL = TimeToLive (key başına explicit deadline)
- TTL daha öngörülebilir; LRU memory pressure'a bağımlı

**Mülakat:** *Cache eviction policy?*
> Redis policy'leri (`maxmemory-policy`):
> - `allkeys-lru`: en az kullanılan, key TTL bağımsız
> - `volatile-lru`: sadece TTL'i olan key'ler arasında LRU
> - `allkeys-lfu`: Least Frequently Used (Redis 4+)
> - `volatile-ttl`: TTL'e en yakın olanı sil
> - `noeviction`: doluysa write reddedilir (default)
>
> Cart için `volatile-ttl` mantıklı (en eski TTL'liyi sil) — biz TTL yönetimini app-side yaptığımız için Redis policy'si arka planı temizler.

---

## 5. Profile-based bean selection

`@Profile("test")` ve `@Profile("!test")` ile mutually exclusive bean'ler. Spring context start'ta sadece bir tanesi yüklenir.

**Alternatifler:**

| Approach | Pros | Cons |
|---|---|---|
| **`@Profile`** (bizim) | Built-in, profile management Spring native | Profile naming conventions bilinmeli |
| `@ConditionalOnProperty` | Daha esnek (any prop value) | Property naming custom, less standard |
| `@ConditionalOnMissingBean` | Auto-config style | Bean order matters, fragile |
| Manual `@Configuration` switch | Tam kontrol | Boilerplate |

**Mülakat:** *Test'te InMemory, prod'da Redis — bunu nasıl yaparsın?*
> 3 yaklaşım: (1) Profile (bizim, idiomatic), (2) `@TestConfiguration` test'te override, (3) `@MockBean CartStore` ile tamamen mock. Profile en temiz çünkü production code unaware — test'in production'la **mümkün olduğunca aynı path'i** kullanmasını istiyoruz.

---

## 6. Testcontainers — `@DynamicPropertySource`

```java
@Container
static GenericContainer<?> redis =
    new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

@DynamicPropertySource
static void redisProps(DynamicPropertyRegistry r) {
  r.add("spring.data.redis.host", redis::getHost);
  r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
}
```

**Mekanik:**
1. Testcontainers Redis'i random host port ile başlatır (e.g. `52341 → 6379`)
2. `@DynamicPropertySource` Spring property'lerini runtime'da set eder
3. Spring Data Redis bu property'leri okur, doğru host/port'a connect olur
4. Test bittiğinde container temizlenir

**`@Container` static**: Testcontainers JUnit 5 extension static container'ı tüm test class için bir kez başlatır (tüm metodlar paylaşır). Non-static instance per-test (yavaş).

**Mülakat:** *Testcontainers vs Spring Boot Embedded Redis?*
> Embedded Redis (Java implementation) **kalmadı** — `embedded-redis` library production-grade değil, abandoned. Testcontainers de facto standart: real Redis, contracts kanıtlanmış, port mapping otomatik.

**Mülakat:** *`@DynamicPropertySource` vs `@TestPropertySource`?*
> Static (`@TestPropertySource`) compile-time bilinen değerler. Dynamic runtime'da hesaplanan değerler (container port, generated UUID, vb.). Testcontainers için `@DynamicPropertySource` zorunlu çünkü port her seferinde farklı.

---

## 7. Cart restart persistence

**Senaryo:** User cart'a 3 ürün ekledi → `mvn spring-boot:run` durdur + tekrar başlat → cart hala 3 ürün gösteriyor mu?

Phase 3 (InMemory): ❌ JVM'de map var, restart'ta kaybolur.
Phase 4 (Redis): ✅ Redis dış process, JVM bağımsız.

**Smoke verify:**
```bash
ACCESS=$(curl -s -X POST http://localhost:8080/api/auth/login -d '{...}')
curl -X POST http://localhost:8080/api/cart/items -H "Bearer $ACCESS" -d '{"productId":1,"quantity":2}'
# JVM'de cart-service'i durdur (Ctrl+C)
mvn -pl services/cart-service spring-boot:run  # tekrar başlat
curl http://localhost:8080/api/cart -H "Bearer $ACCESS"  # cart hala dolu
```

**Redis CLI'da doğrulama:**
```bash
docker exec -it ecommerce-redis redis-cli
> KEYS cart:*
1) "cart:42"
> GET cart:42
"{\"userId\":42,\"items\":[{\"productId\":1,...}],\"updatedAt\":\"2026-05-02T...Z\"}"
> TTL cart:42
(integer) 2592000   # 30 days in seconds
```

---

## 8. Multi-user concurrency — Redis çözümler mi?

InMemory'de `ConcurrentHashMap` ile thread safety vardı. Redis'te?

**Redis tek-threaded** (single command at a time per database). `SET cart:42 ...` atomik. Race condition yok.

**Ama:** read-modify-write pattern'i (get → modify → save) atomik değil:
```java
Cart cart = store.get(userId);          // SELECT cart:42
cart = cart.upsertItem(newItem);         // local mutation
store.save(cart);                        // SET cart:42
```

İki request aynı anda gelirse race var:
- T1 GET cart:42 → 1 item
- T2 GET cart:42 → 1 item
- T1 modify → 2 items, SET
- T2 modify → 2 items, SET → T1'in update'i overwrite!

**Çözümler (Phase 4'te implement edilmedi, sözel):**
- **Optimistic locking via WATCH/EXEC** (MULTI transaction)
- **Distributed lock** (Redisson `RLock`)
- **Cart-level versioning** (Cart record'a `version` field, save'de version check)

**Niye Phase 4'te yok?** Single-user mode için cart concurrency rare. Production'da Redis transaction ekle (Phase 5 Order Saga ile birlikte).

**Mülakat:** *Redis read-modify-write race nasıl çözülür?*
> 3 yol: (1) `WATCH key` + `MULTI/EXEC` — optimistic, conflict'te retry, (2) `SETNX` ile distributed lock + Redisson — pessimistic, (3) Lua script ile atomic eval — sunucu-side. Performance: Lua > Optimistic > Lock (ne kadar contention'a göre). Cart için optimistic genelde yeterli.

---

## 9. Hash data type alternatifi — niye String JSON kullandık?

Redis'te 5 main data type: String, Hash, List, Set, Sorted Set.

Cart'ı şöyle de modelleyebilirdik:

**Hash approach:**
```
HSET cart:42 itemCount 3 totalAmount 30.50 updatedAt "2026..."
HSET cart:42:items <productId-1> "{name, price, qty}"
HSET cart:42:items <productId-2> "{...}"
```

**String approach (bizim):**
```
SET cart:42 "{userId:42, items:[...], updatedAt:...}"
```

**Hash avantajları:**
- ✅ Partial updates (`HSET cart:42:items 5 "..."`) — sadece bir item update
- ✅ Field-level read (`HGET cart:42 totalAmount`)
- ✅ Memory efficient (small hashes use ziplist)

**String avantajları (bizim):**
- ✅ Atomic full-state operations
- ✅ Tek serialize/deserialize cycle
- ✅ Cart full state tek query'de
- ✅ Application code basit

**Bizim use case:** Cart her zaman bir bütün okunup yazılıyor (full snapshot). String JSON yeterli. Hash ancak `add to cart`'ta partial update istersen kazanç sağlar.

**Mülakat:** *Hash vs String trade-off?*
> Hash: granular updates, field-level read. String: atomic full-snapshot, basit. Eğer state'in çok büyük ise (örn. 1000+ items), Hash partial update kazandırır. Cart 5-50 item arası, String OK. Order detail (1000+ line items) ise Hash daha mantıklı.

---

## 10. Phase 4'te yazılan dosyaların haritası

```
services/cart-service/
├── pom.xml                                                # +spring-boot-starter-data-redis, +testcontainers
├── src/main/java/com/backendguru/cartservice/
│   ├── cart/
│   │   ├── InMemoryCartStore.java                         # +@Profile("test")
│   │   └── RedisCartStore.java                            # NEW: @Profile("!test"), TTL 30 days
│   └── config/RedisConfig.java                            # NEW: cartRedisTemplate + Jackson + JavaTimeModule
└── src/test/java/com/backendguru/cartservice/
    ├── CartServiceApplicationTests.java                   # +@ActiveProfiles("test")
    ├── CartFlowIntegrationTest.java                       # +@ActiveProfiles("test")
    └── cart/RedisCartStoreTest.java                       # NEW: Testcontainers + @DynamicPropertySource

infrastructure/config-server/src/main/resources/configs/
├── cart-service.yml                                       # +spring.data.redis.* (host, port 6380, timeout, pool)
└── cart-service-docker.yml                                # NEW: host=redis port=6379 (Phase 12)

docker-compose.yml                                          # +redis service on host port 6380
```

**40 test green** in cart-service:
- 10 `CartTest` (record behavior)
- 4 `InMemoryCartStoreTest`
- 12 `CartServiceTest` (mocked ProductClient + InMemory)
- 7 `CartFlowIntegrationTest` (WireMock + InMemory)
- 6 `RedisCartStoreTest` (Testcontainers + RedisCartStore)
- 1 `CartServiceApplicationTests`

---

## 11. Mülakatta Faz 4 hikayesi

> Phase 3'te kurduğum `CartStore` Strategy interface'ine RedisCartStore implementation'ı ekledim. Service-layer code dokunmadan profile-based bean selection ile swap: `@Profile("test")` InMemoryCartStore (existing tests bozulmasın), `@Profile("!test")` RedisCartStore (dev/docker/prod). RedisTemplate Jackson `Jackson2JsonRedisSerializer` + `JavaTimeModule` ile Cart record'unu (Instant, BigDecimal, List) serialize ediyor. Cart key `cart:<userId>`, value JSON, TTL 30 gün her save'de yenileniyor (cart abandonment cleanup). Lettuce client (Spring Boot default, Netty-based, thread-safe). Testcontainers `redis:7-alpine` + `@DynamicPropertySource` ile yeni `RedisCartStoreTest` save/get/clear/multi-user/TTL davranışını gerçek Redis instance'ında kanıtlıyor. Phase 3'ten 34 test → Phase 4'te 40 test (6 yeni Redis test). docker-compose'a redis 6380 portu eklendi (existing dev redis 6379 ile çakışmasın).

---

## 12. Mülakat soruları (kısa liste)

1. Strategy pattern Phase 3'te interface, Phase 4'te yeni implementation — Open/Closed nasıl uygulandı?
2. `@Profile` vs `@ConditionalOnProperty` vs `@MockBean` — ne zaman hangisi?
3. Lettuce vs Jedis — niye Lettuce?
4. Jackson record serialization — `JavaTimeModule` neden gerek?
5. `BigDecimal`'ı JSON'a number mu string mi yazarsın?
6. Cart key format `cart:<userId>` — niye prefix?
7. TTL niye yenileniyor (every save)?
8. Redis eviction policies — cart için hangisi?
9. Read-modify-write race — Redis'te nasıl çözersin?
10. Hash vs String data type — cart için hangisi?
11. Testcontainers `@DynamicPropertySource` — neden?
12. Connection pool sizing — formul ne?
13. Cart restart sonrası persist olmalı mı? Production'da değil dev'de?
