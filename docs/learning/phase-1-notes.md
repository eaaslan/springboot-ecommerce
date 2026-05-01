# Faz 1 — User Service + JWT (Türkçe Öğrenme Notları)

> Bu dosya Faz 1'in **mülakat hazırlığı** odaklı dökümantasyonu. Kod nerede, niye öyle yazıldı, alternatifler nelerdi ve mülakatta nasıl sorulabilir.

---

## 1. Genel Yapı

`services/user-service` (port 8081) — kullanıcı kimliği, JWT auth (access + refresh), BCrypt password, PostgreSQL persistence.

**Akış:**
```
POST /api/auth/register     → User oluştur (BCrypt hash)
POST /api/auth/login        → Access + Refresh JWT döner
POST /api/auth/refresh      → Refresh ile yeni pair (rotation + replay detect)
GET  /api/users/me          → Bearer token ile user bilgisi
```

API Gateway HS256 imzayı validate eder, `X-User-Id` ve `X-User-Role` header'larıyla downstream'e propagate eder.

---

## 2. JWT — Niye iki token (access + refresh)?

**Access token** (15 dk):
- Kısa ömürlü, her API çağrısına eklenir
- DB lookup yok (stateless), JWT içindeki claim'ler yeterli
- Süresi kısa olduğu için çalınması durumunda hasar sınırlı

**Refresh token** (7 gün):
- Sadece `/api/auth/refresh` için kullanılır
- DB'de hash'lenmiş halde tutulur (`token_hash` SHA-256)
- Replay'i tespit edebilmek için her kullanımda **rotate** edilir (eski revoked, yeni issue)
- Eski refresh tekrar gelirse → **token theft tespiti** → tüm refresh'leri sil, kullanıcı yeniden login olsun

**Mülakat sorusu:** *Niye sadece access token yetmiyor?*
> Stateless olsun isterken kullanıcının her 15 dakikada bir login olmasını istemiyoruz. Refresh token uzun ömürlü, düşük yüzeyli (sadece refresh endpoint'i). Access token'ın çalınması 15 dakikalık hasar; refresh token'ın çalınması rotation sayesinde tek kullanımlık (hırsız kullanırsa gerçek kullanıcı bir sonraki refresh'te 401 alır → token theft tespit edilir).

**Mülakat sorusu:** *Niye refresh token'ı DB'de SHA-256 hash'liyorsun?*
> DB dump çalınırsa düz token kullanılarak refresh edilebilir; hash'leyince gerçek token'a sahip olmayan saldırgan refresh edemez. Aynı password storage mantığı: client tarafında JWT, server tarafında SHA-256.

---

## 3. BCrypt vs alternatives

`BCryptPasswordEncoder(12)` — strength 12 (`2^12 = 4096` round). Modern donanımda ~250ms (login zamanına ekleniyor, kullanıcı fark etmiyor; brute-force çok yavaş).

| Algorithm | Properties |
|---|---|
| **MD5/SHA-1** | ❌ Asla. Hash hızlı, GPU brute-force kolay |
| **SHA-256** | ❌ Tek başına. Salt yok, hızlı |
| **PBKDF2** | ⚠️ OK ama BCrypt daha iyi |
| **BCrypt** | ✅ Adaptive, salt dahili. Endüstri standardı |
| **Argon2id** | ✅✅ Modern öneri (2015 PHC kazananı), BCrypt'ten güçlü ama Spring desteği daha az olgun |

**Mülakat sorusu:** *Strength 12 mi 14 mü?*
> Strength her +1 = 2x yavaş. 12 (~250ms) login UX için sınır. Test'lerde 4 kullanılır (saniyeler yerine ms). `passwordEncoder.upgradeEncoding()` ile gelecekte legacy hash'ler arttırılabilir.

---

## 4. N+1 problem ve `@EntityGraph`

`User` entity'sinin `@OneToMany List<Address> addresses` field'ı **lazy**. Eğer 10 user yükleyip her birinin address'ine erişirsen → 1 (user yükleme) + 10 (her user için address) = **11 query**. 100 user için 101 query. **N+1 problemi**.

**Çözümler (en güçlüden en zayıfa):**

| Çözüm | Mekanizma | Pro | Con |
|---|---|---|---|
| `@EntityGraph(attributePaths = "addresses")` | Repository annotation, JPA-level | ✅ Bizim seçimimiz; tek query, declarative | Method başına bir graph |
| `JOIN FETCH` (JPQL) | `@Query("select u from User u join fetch u.addresses")` | Esnek | Pageable ile çakışır (cartesian) |
| `@BatchSize(size = 50)` | Hibernate; lazy ama batch'lenir | İyi orta yol | Hala 2 query |
| Reflection-based DTO projection | `interface UserView { String getEmail(); }` | DTO döner, sadece gerekli columns | Karmaşık nested için zor |

**Bizim repository'de:**
```java
@EntityGraph(attributePaths = "addresses")
Optional<User> findWithAddressesById(Long id);
```

Spring Data JPA bunu görüp Hibernate'e "User'ı yüklerken addresses'ı da JOIN ile getir" der. **Tek SQL.**

**Mülakat sorusu:** *N+1 nasıl tespit edilir?*
> Hibernate SQL log'larını aç (`spring.jpa.show-sql=true` veya `org.hibernate.SQL=DEBUG`). Endpoint'i çalıştır, log'daki `SELECT` sayısını say. Test ile: `datasource-proxy-spring-boot-starter` veya Hibernate `Statistics` ile query count assert.

**Mülakat sorusu:** *Pageable + JOIN FETCH = neden tehlikeli?*
> `JOIN FETCH` ile pagination → Hibernate "in-memory pagination" warning verir. Cartesian product (1 user × N address) row sayısı patlatır, OFFSET/LIMIT yanlış çalışır. Çözüm: `@EntityGraph` + Pageable (Hibernate ek query atar ama doğru) veya iki aşamalı (önce ID page, sonra fetch).

---

## 5. Optimistic Locking (`@Version`)

`User` entity'de:
```java
@Version
@Column(nullable = false)
private long version;
```

İki kullanıcı aynı anda aynı user'ı modify ederse: ilk commit `version 1`'i görür, increment ile `2`'ye yazar. İkinci commit hala `version 1` zannediyor, `WHERE version = 1` koşulu `0 row affected` → Hibernate `OptimisticLockException` fırlatır.

**Pessimistic locking ile karşılaştırma:**

| | Optimistic | Pessimistic |
|---|---|---|
| Mekanizma | `version` kolonu, exception on conflict | DB row lock (`SELECT ... FOR UPDATE`) |
| Performance | Lock yok, retry stratejisi | Lock = throughput düşüşü |
| Ne zaman? | Conflict nadir | Conflict çok (bank transfer, stok) |
| Bizim use case | User edit → Optimistic | Faz 5'te stok düşürmede pessimistic |

---

## 6. `@Transactional` propagation/isolation

`AuthService.register()`:
```java
@Transactional
public Long register(RegisterRequest req) {
    if (userRepository.existsByEmail(req.email())) throw ...;
    return userRepository.save(user).getId();
}
```

**Propagation seçenekleri:**

| Option | Davranış | Use case |
|---|---|---|
| `REQUIRED` (default) | Var olan tx'e join, yoksa yeni başlat | %95 use case |
| `REQUIRES_NEW` | Yeni tx aç (parent suspend) | Audit log, outbox |
| `NESTED` | Savepoint | Kısmi rollback |
| `SUPPORTS` | Varsa join, yoksa tx'siz | Read-only metoda |
| `NOT_SUPPORTED` | Var olanı suspend et | Tx-toxic kod |
| `MANDATORY` | Yoksa hata | Helper metoda |
| `NEVER` | Varsa hata | Yok denir |

**Isolation:**
- `READ_UNCOMMITTED` — dirty read, asla
- `READ_COMMITTED` (default Postgres) — dirty read engellenir, non-repeatable read olabilir
- `REPEATABLE_READ` — phantom read olabilir (ama Postgres'te değil — snapshot)
- `SERIALIZABLE` — en güçlü, en yavaş

**Bizim register:** `REQUIRED` + Postgres default `READ_COMMITTED`. Yeterli çünkü `existsByEmail` + `save` arasında race condition için unique constraint backstop var (`UNIQUE` index → DB-level guard).

---

## 7. Spring Security filter chain

```java
http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
```

**Spring Security'nin filter sırası (özet):**
1. `SecurityContextPersistenceFilter` — context yükle
2. `LogoutFilter`
3. `**JwtAuthenticationFilter (bizim, before UPAuth)**`
4. `UsernamePasswordAuthenticationFilter` — form login (kullanmıyoruz)
5. `BasicAuthenticationFilter`
6. `RequestCacheAwareFilter`
7. `SecurityContextHolderAwareRequestFilter`
8. `AnonymousAuthenticationFilter`
9. `SessionManagementFilter` (stateless'ta no-op)
10. `ExceptionTranslationFilter` — auth/access exception → 401/403
11. `FilterSecurityInterceptor` — authorize decision

**Mülakat sorusu:** *Niye `addFilterBefore`?*
> `UsernamePasswordAuthenticationFilter`'dan **önce** koymalıyız ki form-login filter'ı request'imizi yakalamasın. JWT zaten `Authentication` set etmiş, sonraki filter'lar "context'te auth var" diye geçer.

**Mülakat sorusu:** *CSRF niye disable?*
> CSRF token cookie/session tabanlı. Stateless JWT API'de cookie kullanmıyoruz, attack vektörü yok. CSRF disable. **AMA** SPA + cookie-based JWT kullanıyorsak CSRF tekrar gerekli — token Authorization header yerine cookie'de ise.

---

## 8. Refresh token rotation — replay detection

```java
// AuthService.refresh()
1. JWT parse (signature + expiry valid?)
2. SHA-256 hash, refresh_tokens'ta ara
3. Bulunamazsa → 401 "Refresh not found (replay?)"
4. revoked_at != null → 401 "Already revoked"
5. revoked_at = now() (rotation: eski iptal)
6. Yeni access + refresh issue, yeni hash kaydet
```

**Saldırı senaryosu:** Saldırgan refresh token'ı çaldı (örn. man-in-the-middle).

- Saldırgan refresh kullanır → yeni pair alır, eskisi revoked
- Gerçek kullanıcı eski (artık revoked) refresh ile gelir → 401 "revoked" → **uygulama**: "tüm refresh token'larını sil, yeniden login ol" → token theft tespit
- Saldırganın elindeki yeni refresh hala geçerli ama kullanıcı yeniden login olunca o da silinir

**Mülakat sorusu:** *Refresh rotation'ı niye DB'de tutuyoruz, JWT yetmez mi?*
> JWT stateless ⇒ revocation imkansız. Refresh'i DB'de tutmazsak çalan refresh süresi dolana kadar kullanır. DB'de hash kayıtlı olunca rotation + revocation mümkün.

---

## 9. Testcontainers vs H2

**H2** (`UserServiceApplicationTests`):
- Smoke test (`contextLoads`)
- `@AutoConfigureTestDatabase(replace = ANY)` — datasource'u H2 ile değiştirir
- `flyway.enabled=false` çünkü H2'de Postgres SQL syntax çalışmayabilir
- Hızlı, ama **gerçek DB davranışı değil** (constraint'ler, type coercion farklı)

**Testcontainers** (`UserRepositoryTest`, `AuthFlowIntegrationTest`):
- `@Container @ServiceConnection static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")`
- Spring Boot 3.1+ `@ServiceConnection` otomatik datasource bind eder
- Gerçek Postgres (Flyway, indexler, constraint'ler test edilir)
- Yavaş (image pull + container start ~10s) ama **gerçek davranış**

**Mülakat sorusu:** *Hangisini ne zaman?*
> Smoke test (context loads) → H2 ile yeterli, hızlı. Repository / integration test → Testcontainer. Production'la aynı DB'de test = "production-parity" = "works on prod, works in tests" garantisi.

---

## 10. MapStruct (`UserMapper`)

```java
@Mapper(componentModel = "spring")
public interface UserMapper {
  @Mapping(target = "addresses", ignore = true)
  UserResponse toResponse(User user);
  // ...
}
```

**Niye MapStruct?**
- ✅ Compile-time generated (zero runtime reflection)
- ✅ Type-safe (mismatch → compile error)
- ✅ Generated kodu okuyabilirsin
- ✅ Lombok'la beraber çalışır (`lombok-mapstruct-binding` ayarlandı parent pom'da)

**Alternatifler:**
- ModelMapper, Dozer → reflection runtime, yavaş
- Manuel mapping → boilerplate, ama refactor güvenli (IDE bulur)

**Mülakat sorusu:** *MapStruct vs ModelMapper?*
> Compile-time vs runtime. MapStruct'ta yanlış field adı → derleme hatası. ModelMapper'da → runtime exception. MapStruct kazanır production code için.

---

## 11. API Gateway JWT validation

`GatewayJwtAuthenticationFilter` reactive `WebFilter`:
1. `/api/auth/`, `/actuator/`, swagger → bypass (public)
2. `GET /api/products/**` → bypass + `X-User-*` strip (public catalog)
3. Diğerleri → `Authorization: Bearer ...` zorunlu, parse, downstream'e `X-User-Id` + `X-User-Role` propagate
4. Token yok/invalid → 401 with `ErrorResponse` JSON

**Trust model:**
- Gateway = **edge** — JWT validate
- User Service = **JWT yine parse eder** (defense in depth — eğer gateway bypass edilirse)
- Product Service / Cart Service = **header'a güven** (gateway arkasında, internal network)

**Mülakat sorusu:** *Header trust mu, JWT-everywhere mi?*
> Trade-off. JWT-everywhere = signature key her servise dağıtılır, replay olur, defense-in-depth. Header-trust = az kod, hızlı, internal network'e güven varsayımı. Production'da Service Mesh (Istio mTLS) ile internal trafiği imzalarsın — header-trust + mTLS kombinasyonu en yaygın.

---

## 12. Mülakatta Faz 1 hikayesi

> Spring Boot 3 / Spring Cloud 2024.0 üzerinde stateless JWT auth tasarladım. PostgreSQL + Flyway migration ile users, addresses, refresh_tokens tabloları. Access token (HS256, 15 dk) stateless, refresh token (7 gün) DB'de SHA-256 hash'li tutuluyor — rotation ile replay detection sağlandı. BCrypt strength 12 password hashing. `User → Address` `@OneToMany` lazy ilişkisi `@EntityGraph` ile N+1'i çözüyor — Testcontainers ile gerçek Postgres'te assert ettim. Spring Security stateless: CSRF disable, custom `JwtAuthenticationFilter` `UsernamePasswordAuthenticationFilter`'dan önce. API Gateway reactive `WebFilter` ile JWT'yi edge'de validate edip downstream'e `X-User-*` header'larıyla propagate ediyor — defense in depth için User Service de JWT'yi yeniden parse ediyor. Swagger UI bearer scheme ile her servisin endpoint dökümantasyonunu açıyor.

---

## 13. Faz 1'de yazılan dosyaların haritası

```
services/user-service/
├── pom.xml                                                # web, jpa, security, jjwt, testcontainers, springdoc
├── src/main/
│   ├── java/com/backendguru/userservice/
│   │   ├── UserServiceApplication.java                    # @EnableDiscoveryClient, @ConfigurationPropertiesScan
│   │   ├── auth/
│   │   │   ├── AuthService.java                           # register/login/refresh/logout (BCrypt + SHA-256)
│   │   │   ├── AuthController.java                        # /api/auth/*
│   │   │   ├── dto/{Register,Login,Refresh}Request, TokenResponse
│   │   │   ├── jwt/
│   │   │   │   ├── JwtProperties.java                     # @ConfigurationProperties("app.jwt")
│   │   │   │   ├── JwtService.java                        # HS256, access+refresh issuance, parse
│   │   │   │   └── JwtAuthenticationFilter.java           # OncePerRequestFilter
│   │   │   ├── refresh/
│   │   │   │   ├── RefreshToken.java                      # entity (id, userId, tokenHash, expires, revoked)
│   │   │   │   └── RefreshTokenRepository.java
│   │   │   └── security/SecurityConfig.java               # filter chain, BCrypt(12)
│   │   ├── user/
│   │   │   ├── User.java, Address.java, Role.java         # entities + enum
│   │   │   ├── UserRepository.java                        # @EntityGraph for addresses
│   │   │   ├── UserService.java, UserController.java
│   │   │   ├── dto/{UserResponse, UserMapper}             # MapStruct
│   │   │   └── exception/GlobalExceptionHandler.java
│   │   └── config/OpenApiConfig.java                      # springdoc bearer scheme
│   └── resources/
│       ├── application.yml, logback-spring.xml
│       └── db/migration/V1__init_users.sql
└── src/test/java/com/backendguru/userservice/
    ├── UserServiceApplicationTests.java                   # H2 smoke
    ├── jwt/JwtServiceTest.java                            # unit
    ├── user/UserRepositoryTest.java                       # Testcontainer
    └── auth/AuthFlowIntegrationTest.java                  # Testcontainer + MockMvc

infrastructure/api-gateway/src/main/java/.../jwt/GatewayJwtAuthenticationFilter.java
infrastructure/config-server/src/main/resources/configs/{user-service,user-service-dev}.yml
```

## 14. Mülakat soruları (kısa liste)

1. JWT vs session — niye stateless seçtin?
2. Refresh token rotation nasıl çalışıyor? Replay detection?
3. BCrypt strength 12 niye? 4 niye değil?
4. N+1 problem nedir, nasıl çözdün, nasıl test ettin?
5. `@EntityGraph` vs `JOIN FETCH` vs `@BatchSize`?
6. Optimistic vs Pessimistic locking, `@Version`?
7. `@Transactional` propagation — `REQUIRED` vs `REQUIRES_NEW`?
8. Postgres isolation level default? Phantom read olur mu?
9. Spring Security filter chain order — niye `addFilterBefore`?
10. CSRF stateless API'de niye disable?
11. HS256 vs RS256 — ne zaman hangisi?
12. Testcontainers vs H2 trade-off?
13. MapStruct vs ModelMapper?
14. Gateway header trust vs JWT-everywhere?
