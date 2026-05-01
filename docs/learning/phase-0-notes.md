# Faz 0 — Microservice Foundation (Türkçe Öğrenme Notları)

> Faz 0'ın **mülakat hazırlığı** dokümantasyonu. Kuruluş kararları, alternatifler, mülakat soruları.

---

## 1. Proje yapısı — niye monorepo + Maven multi-module?

**Monorepo:** tüm servisler tek git repo'da. **Multi-module:** her servis kendi `pom.xml`'i, parent POM bağlar.

**Avantajlar:**
- ✅ Tek `git clone` demo
- ✅ Atomic refactor (5 servisi etkileyen değişiklik tek PR)
- ✅ Shared dependency versiyonları DRY (parent dependencyManagement)
- ✅ Spring Cloud BOM tek yerde

**Dezavantajlar:**
- ❌ Build süresi artar (büyük ekiplerde Bazel/Gradle daha hızlı)
- ❌ Production'da gerçek microservice = polyrepo (independent deploy)
- ❌ Tek `pom.xml` sorunu sistemi etkiler

**Mülakat:** *Monorepo vs polyrepo?*
> Google/Meta/Uber monorepo, GitHub/Netflix polyrepo. Trade-off: monorepo'da atomic değişiklik kolay ama scaling sorun; polyrepo'da bağımsızlık ama cross-cutting değişiklik 5 PR. Bizim öğrenme/portfolio için monorepo doğru — tek demo URL.

---

## 2. Spring Cloud release train

`spring-cloud-dependencies` BOM'unu `pom` `import` scope'la dependencyManagement'a alıyoruz. **Release train**: Spring Cloud bileşenlerinin (Eureka, Gateway, Config, Resilience4j) birbirleriyle uyumlu versiyonlarının paketi. **2024.0.x** kod adı **Moorgate** — Spring Boot 3.4 ile uyumlu.

```xml
<spring-cloud.version>2024.0.0</spring-cloud.version>
```

Hangi Spring Boot ile hangi Cloud uyumlu — Spring Cloud release notes'a bak. Yanlış kombinasyon: classpath conflict → mysterious bean errors.

**Mülakat:** *Spring Cloud release train nedir?*
> Spring projesi gibi her bileşen kendi versiyonu olsa, hangileri uyumlu kullanıcı bilemez. Release train tek bir BOM'da uyumlu versiyon setini paketler. Sen sadece BOM versiyonunu seçersin (`2024.0.0`), Cloud doğru bileşen versiyonlarını verir.

---

## 3. `@ConditionalOnWebApplication` — kütüphane hem Servlet hem Reactive'i nasıl destekler?

`shared/common` modülünde iki filter var:
- `CorrelationIdFilter` (Servlet, `jakarta.servlet.Filter`)
- `CorrelationIdWebFilter` (Reactive, `org.springframework.web.server.WebFilter`)

Her ikisi `@Component`. Ama bir Servlet uygulamasında Reactive filter classpath'te yok → `BeanCreationException`.

**Çözüm:**
```java
@Component
@ConditionalOnClass(name = "jakarta.servlet.Filter")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CorrelationIdFilter extends OncePerRequestFilter { ... }

@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
public class CorrelationIdWebFilter implements WebFilter { ... }
```

Spring Boot context start ederken stack'i tespit eder, sadece doğru filter'ı yükler.

**`pom.xml`'de:**
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
    <optional>true</optional>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
    <optional>true</optional>
</dependency>
```

`optional`: compile time'da var ama transitive olarak consumer'a yayılmaz. Consumer (örn. user-service) `web` ekler, gateway `webflux` ekler.

---

## 4. Reactor Context — neden ThreadLocal yerine?

Reactive akışta bir request farklı thread'lerde devam eder (event loop, scheduler). ThreadLocal sadece **bir** thread'e bağlı.

**Servlet (ThreadLocal):**
```java
MDC.put("traceId", id);   // ThreadLocal'a yaz
chain.doFilter(...);        // aynı thread, MDC.get çalışır
MDC.clear();               // clean up
```

**Reactive (Reactor Context):**
```java
return chain.filter(exchange)
    .contextWrite(ctx -> ctx.put("traceId", id));  // immutable context, downstream'e yayılır
```

Sonra service code'unda:
```java
Mono.deferContextual(ctx -> {
    String trace = ctx.get("traceId");
    return ...;
});
```

**Mülakat:** *Reactive'de MDC nasıl çalışır?*
> ThreadLocal MDC çalışmaz. Reactor Context immutable, akışla beraber yayılır. SLF4J 2.x + reactor-core mikro-meter ile MDC'ye context bind edilebilir; ya da manuel `Mono.deferContextual` ile context'i okuyup `MDC.put` + `MDC.clear`.

---

## 5. Eureka — niye AP (CAP teoreminde)?

CAP: Consistency, Availability, Partition tolerance — biri seç. Eureka **AP**: network partition'da availability'yi koru, stale data dön.

**Senaryo:** Eureka cluster (3 node), 1 ayrı kalır.
- AP: 3 node da cevap vermeye devam, ayrı node "her şey UP" zannedebilir (stale)
- CP: ayrı node cevap vermez, registry tutarlı ama unavailable

**Niye AP?** Service discovery'de "discovery yapılamıyor" = sistem çöker. "Stale ama hala cevap" = client side balancer / circuit breaker stale instance'ı yakalar.

**Self-preservation:**
- Production: ON. Network glitch'te "%85 instance heartbeat alamıyorum" = panic değil, korumaya geç. Yoksa tüm fleet'i evict eder.
- Dev: OFF. Lokal test'te servisi durdurursun, hemen Eureka'dan düşmesi gerek. Bizim `dev` profile'da OFF.

**Mülakat:** *Eureka vs Consul?*
> Eureka AP, Consul CP. Consul'a yazma daha tutarlı ama partition'da yazma reddedilir. Service discovery için AP genelde tercih (availability kritik). Service mesh (Istio/Linkerd) zaten kendi discovery'sini yapar, library-based registry'ye gerek azalır.

---

## 6. API Gateway — niye reactive?

`spring-cloud-starter-gateway` Netty + WebFlux tabanlı (reactive). Servlet alternatifi `spring-cloud-starter-gateway-mvc` da var (Spring 6+) ama daha az olgun.

**Niye reactive gateway için ideal?**
- ✅ I/O-bound: gateway çoğunlukla "ben request'i forward ediyorum, downstream cevabını bekliyorum" — thread-per-request modeli boşa thread harcar
- ✅ Yüksek concurrency: 10K connection için Servlet 10K thread (>1GB stack) lazım, Netty event loop küçük thread pool'la halleder
- ✅ Backpressure: yavaş downstream upstream'i throttle edebilir

**Domain service (user, product) niye Servlet?**
- ✅ JPA/Hibernate **blocking**. Reactive'de `R2DBC` var ama JPA ekosistemini bırakırsın
- ✅ Daha tanıdık model, debugging kolay
- ✅ Java 21 virtual threads ile blocking servlet kodu near-reactive performansta

**Mülakat:** *Reactive ne zaman kullanırım?*
> Gateway, aggregator, streaming, WebSocket, server-sent events. Domain service'lerde özel ihtiyaç yoksa Servlet + Java 21 virtual threads yeterli.

---

## 7. Centralized config — Config Server

`infrastructure/config-server` (8888) `native` filesystem backend kullanıyor. `classpath:/configs/<service-name>(-<profile>).yml` dosyalarını serve eder.

**Native vs Git backend:**

| Backend | Use case | Pros | Cons |
|---|---|---|---|
| `native` (filesystem) | dev, demo | Hızlı, repo dahili | Config rotation/audit yok |
| `git` | production | Versiyonlama, audit, rollback | Setup, git access gerek |
| `vault` | secrets | Encrypted, dynamic credentials | Vault server gerekir |

Bizim Phase 0 native, Phase 12'de Git backend'e geçilebilir.

**Refresh:** `@RefreshScope`'lu bean'ler `/actuator/refresh` POST'la config yenilenir. Hot reload (restart yok).

**Mülakat:** *Centralized config'in alternatifi?*
> Kubernetes ConfigMap + Secret. Pod restart ile yeniden okunur (rolling restart için Reloader). Avantaj: K8s native, RBAC dahil. Dezavantaj: K8s'e bağlı, audit zayıf.

---

## 8. RFC 7807 — Problem Details for HTTP APIs

```json
{
  "code": "RESOURCE_NOT_FOUND",
  "message": "Product 42 not found",
  "status": 404,
  "path": "/api/products/42",
  "traceId": "abc-123",
  "timestamp": "2026-04-26T10:23:45Z",
  "details": { "field": "..." }
}
```

RFC 7807 standart structure: `type`, `title`, `status`, `detail`, `instance`. Bizim alan adlarımız RFC tam aynı değil ama esinli.

**Niye envelope (`ApiResponse<T>` ile sarıldık)?**
- ✅ Frontend daima aynı şape parse eder
- ✅ `success` flag ile fast-check
- ✅ Pagination meta için yer

**Niye RFC 7807 değil direkt?**
- Public API için RFC 7807 standartı tercih edilebilir
- Bizim internal/learning project için envelope + RFC-flavoured error iç içe daha pratik

**Mülakat:** *Standard error response shape?*
> Public API → RFC 7807 application/problem+json. Internal API → kendi convention'ın olabilir (envelope vs direct). En önemli: **tutarlılık**, frontend tek logic'le tüm endpoint'leri parse edebilsin.

---

## 9. Profile strategy

3 profile: `dev` (default), `docker`, `prod`.

| | dev | docker | prod |
|---|---|---|---|
| Loglama | plain text (terminal) | JSON | JSON |
| Service URLs | `localhost:*` | service-name DNS | production DNS |
| Eureka self-preservation | OFF | ON | ON |
| Config backend | native fs | native fs (image-baked) | git-backed |
| Hibernate SQL | DEBUG (dev override) | INFO | WARN |

`spring.profiles.active` env var ile geçiş. CI/CD pipeline'da `docker` profile ile container build, prod deploy'da `prod` profile.

**Mülakat:** *Sensitive secrets nasıl yönetilir?*
> Asla yaml'a hardcode etme. `${JWT_SECRET}` env var pattern. Production'da Vault, AWS Secrets Manager, GCP Secret Manager. Bizim default (`dev-only-256-bit-key-please-change...`) açıkça uyarı içeriyor.

---

## 10. Spotless + Google Java Format

Parent POM'da:
```xml
<plugin>
    <groupId>com.diffplug.spotless</groupId>
    <artifactId>spotless-maven-plugin</artifactId>
    <executions>
        <execution>
            <goals><goal>check</goal></goals>
            <phase>verify</phase>
        </execution>
    </executions>
</plugin>
```

`mvn verify` → format check. Bozuk format → BUILD FAILURE. CI'da merge'i engelle. Local fix: `mvn spotless:apply`.

**Niye?**
- ✅ Code review'da format tartışması yok
- ✅ Diff'ler temiz (whitespace değişiklik yok)
- ✅ Onboarding hızlı (standart format dikte ediliyor)

**Alternatifler:** Checkstyle (rule-based, daha esnek), Prettier (frontend için), EditorConfig (otomatik IDE format ama enforcement yok).

**Mülakat:** *Code style nasıl enforce edilir?*
> Local: `.editorconfig` (IDE level). Build: Spotless/Checkstyle (compile-time). CI: PR check + format-applied bot. Pre-commit hook: `husky` (Node), `pre-commit` (Python). Layered defense.

---

## 11. Multi-module Maven mechanics

Parent POM `<packaging>pom</packaging>` (artifact üretmez, sadece coordination). Child POM `<parent>` ile bağlanır. **dependencyManagement** parent'ta versiyonları toplar, child'lar versiyon yazmadan dependency ekler.

**Annotation processor sırası (Lombok + MapStruct):**
```xml
<annotationProcessorPaths>
    <path><groupId>org.projectlombok</groupId><artifactId>lombok</artifactId></path>
    <path><groupId>org.mapstruct</groupId><artifactId>mapstruct-processor</artifactId></path>
    <path><groupId>org.projectlombok</groupId><artifactId>lombok-mapstruct-binding</artifactId></path>
</annotationProcessorPaths>
```

`lombok-mapstruct-binding` Lombok'un MapStruct'tan **önce** çalışmasını garanti eder (getter'lar üretildikten sonra MapStruct mapper'a bakar).

**Mülakat:** *Lombok + MapStruct birlikte nasıl çalışır?*
> Lombok compile-time getter/setter inject eder, MapStruct compile-time mapper class generate eder. Sıra önemli: Lombok önce çalışmalı ki MapStruct getter'ları görsün. `lombok-mapstruct-binding` `MapStruct → Lombok` order garantiler. Yoksa empty mapper'lar üretilir.

---

## 12. Mülakatta Faz 0 hikayesi

> Java 21 + Spring Boot 3.4 + Spring Cloud 2024.0 (Moorgate) üzerinde multi-module Maven monorepo kurdum. 4 modül: `shared/common` (RFC 7807-flavoured `ErrorResponse`, `ApiResponse<T>` envelope, `BusinessException` hierarchy, hem Servlet hem Reactive için correlation ID filter'ları — `@ConditionalOnWebApplication` ile stack-aware), Config Server (native fs backend, tüm faz config'leri pre-staged), Eureka Server (dev'de self-preservation off), API Gateway (reactive, JWT validation + correlation ID + RFC 7807 error handler + GET /api/products bypass + X-User-* header strip). Profile strategy: dev (plain text logs, localhost), docker (JSON, service-name DNS), prod. Parent POM'da Spotless + Google Java Format `mvn verify` zamanında enforce. Tüm cross-cutting concern'ler 26 unit test ile kanıtlandı.

---

## 13. Mülakat soruları (kısa liste)

1. Monorepo vs polyrepo — projende neden monorepo?
2. Spring Cloud release train nedir? Versiyon uyumluluğu?
3. `@ConditionalOnWebApplication` ne işe yarar? Servlet vs Reactive aynı kütüphanede nasıl?
4. Reactive'de MDC niye ThreadLocal değil? Reactor Context?
5. Eureka — CAP teoreminde nereye düşer? Self-preservation?
6. API Gateway niye reactive? Domain service niye Servlet?
7. Centralized Config Server'ın alternatifleri (native, git, vault, K8s ConfigMap)?
8. RFC 7807 nedir? Envelope vs direct response?
9. Profile strategy — dev/docker/prod arasında ne değişir?
10. Spotless + Checkstyle farkı?
11. Multi-module Maven — `dependencyManagement` vs `dependencies`?
12. Lombok + MapStruct annotation processor sırası niye önemli?
