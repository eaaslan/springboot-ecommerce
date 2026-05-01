# Faz 2 — Product Service (Türkçe Öğrenme Notları)

> Faz 2'nin **mülakat hazırlığı** dokümantasyonu. Pagination, filtering, N+1 çözümleri, header-trust security, soft-delete patterns.

---

## 1. Genel yapı

`services/product-service` (port 8082) — ürün kataloğu. Public browsing (auth gerek değil), admin-only write (`@PreAuthorize("hasRole('ADMIN')")`).

**Akış:**
```
GET  /api/products?categoryId=2&minPrice=300       → public, paginated
GET  /api/products/1                                → public, ürün + category eager
GET  /api/products/categories                       → public, 5 kategori
POST /api/products    (X-User-Role: ADMIN)         → admin only
PUT  /api/products/{id}                             → admin only
DELETE /api/products/{id}                           → soft-delete (enabled=false)
```

API Gateway:
- `GET /api/products/**` → JWT bypass (public catalog), `X-User-*` header'larını strip eder (forgery koruması)
- Diğer methods → Bearer token şart, JWT'den `X-User-*` ekler

---

## 2. N+1 Problem ve `@EntityGraph` — pratik gösterim

`Product.category` `@ManyToOne(LAZY)`. 20 ürün listele + her birinin category'sine eriş = **21 SQL** (1 ürün + 20 category lookup).

**Kötü kod:**
```java
@GetMapping
public List<Product> list() {
    return productRepository.findAll(); // 1 SQL
    // sonra controller'da p.getCategory().getName() denirse → her ürün için ek SQL
}
```

**Çözüm — repository'de override:**
```java
@Override
@EntityGraph(attributePaths = "category")
Page<Product> findAll(@Nullable Specification<Product> spec, Pageable pageable);
```

`@EntityGraph` Hibernate'e "load yaparken `category`'yi de getir" der → **LEFT JOIN ile tek SQL**.

**Test ile kanıt:**
```java
@Test
void findAllWithEntityGraphAvoidsLazyInitException() {
    var page = productRepository.findAll((Specification<Product>) null, PageRequest.of(0, 30));
    em.clear(); // Entity Manager'ı temizle
    page.getContent().forEach(p -> assertThat(p.getCategory().getName()).isNotNull());
    // em.clear() sonrası category access çalışıyorsa → eager yüklenmiş demek
}
```

**Mülakat:** *Pagination + JOIN FETCH neden tehlikeli?*
> JPQL `JOIN FETCH` ile pagination birleştirilirse Hibernate `HHH000104` warning verir ve **in-memory pagination** yapar — yani tüm sonuçları çekip Java'da kesi. Cartesian product (1 ürün × N category) row sayısını şişirir, OFFSET/LIMIT yanlış çalışır. Çözüm: `@EntityGraph` (hala tek query, ama Hibernate doğru hallediyor) veya iki aşamalı (önce ID page, sonra fetch).

---

## 3. Specification API

Dynamic query construction. Filter null ise spec'e dahil edilmez.

```java
public static Specification<Product> hasCategory(Long categoryId) {
    if (categoryId == null) return null;
    return (root, cq, cb) -> cb.equal(root.get("category").get("id"), categoryId);
}
```

Service'te composition:
```java
Specification<Product> spec = ProductSpecifications.enabledOnly();
if (nameSpec != null) spec = spec.and(nameSpec);
if (catSpec != null)  spec = spec.and(catSpec);
// ...
```

**Mülakat:** *Specification vs Criteria API vs `@Query`?*
> - **`@Query` JPQL/native:** static, basit. Filtreler değişkense N varyant gerek.
> - **Criteria API:** dinamik ama verbose. Spring Data altında zaten Criteria kullanır.
> - **Specification API:** Spring Data'nın Criteria üzerine wrapper'ı. Compose edilebilir, type-safe (root.get("..") string-typed AMA), test edilebilir.
>
> Bizim use case (5 optional filter, dinamik combine) için Specification doğru seçim.

**`root.get("category").get("id")`** string-based path. Refactor güvenliği için JPA Static Metamodel kullanılabilir:
```java
cb.equal(root.get(Product_.category).get(Category_.id), id)
```
Compile-time check ama generator setup gerekir. Bizim ölçek için string kabul edilebilir.

---

## 4. Pagination — offset vs cursor

`PageRequest.of(page, size, sort)` → `LIMIT size OFFSET page*size`.

**Offset pagination problemi:** büyük datasette OFFSET 100000 → DB önce 100000 row skip eder, sonra döner = yavaş.

**Cursor pagination:** Son görülen ID'den sonrasını getir
```sql
WHERE id > :lastSeenId ORDER BY id LIMIT 20
```
Pro: O(log n) index seek. Con: random page ziplama yok (1 → 50 → 5).

**Bizim seçim:** Offset (Spring Data `Pageable` default). 20 ürün için problem yok. 1M+ ürün varsa cursor düşünülür.

**Sort whitelist (injection defense):**
```java
private static final Set<String> ALLOWED_SORT = Set.of("id", "name", "priceAmount", "createdAt");
String safeSortBy = ALLOWED_SORT.contains(sortBy) ? sortBy : "id";
```

Aksi halde `?sortBy=password_hash` ile saldırgan column metadata sızdırabilir (timing).

**Mülakat:** *Pagination'da `total_elements` niye yavaş?*
> Spring Data `Page` döndürünce 2 query: `SELECT ... LIMIT 20` + `SELECT count(*) FROM ... WHERE ...`. Count query büyük tabloda yavaş. Çözümler: `Slice` döndür (count yok), cursor pagination, ya da approximate count (Postgres `pg_class.reltuples`).

---

## 5. `open-in-view: false` — neden?

Spring Boot default `spring.jpa.open-in-view=true` → **OSIV (Open Session In View)** açık. Demek ki: HTTP request boyunca Hibernate session açık kalır, controller/view layer'dan da lazy loading mümkün.

**Bizim ayar:** `false` (Config Server'da set edilmiş).

**Niye?**
- ✅ **N+1'i gizler** — controller'dan lazy access çalışır ama her erişim ek SQL
- ✅ **Connection pool tükenmesi** — uzun request'ler DB connection tutar
- ✅ **Transaction sınırı bulanık** — `@Transactional` dışında DB değişikliği yapılabilir

**Trade-off:** Lazy field'a controller'dan erişirsen `LazyInitializationException`. Bunu service layer'da entity graph veya DTO mapping ile çözüyoruz (zaten doğru pattern). Spring Boot 3.x'te default `false` olması tartışıldı ama backward compat için hala `true`.

---

## 6. Soft-delete

```java
@DeleteMapping("/{id}")
public ResponseEntity<Void> delete(@PathVariable Long id) {
    service.softDelete(id); // enabled = false
    return ResponseEntity.noContent().build();
}

@Transactional
public void softDelete(Long id) {
    Product p = productRepository.findById(id).orElseThrow(...);
    p.setEnabled(false); // dirty-checking ile UPDATE atılır
}
```

Hard delete (`DELETE FROM products WHERE id = ?`) yapmıyoruz çünkü:
- ✅ Order tarihçesi ürünü referans ediyor olabilir (cascade ile silersek geçmiş bozulur)
- ✅ Audit/recovery
- ✅ Yasal gereksinimler

**Listing'de soft-deleted'ları gizleme:**
```java
Specification<Product> spec = ProductSpecifications.enabledOnly();
```
Her listing query bu predicate ile başlar.

**Tehlike:** SQL unique constraint patlaması. Eğer sku UNIQUE ise ve "SKU-001" enabled=false olarak duruyorsa, yeni "SKU-001" insert edemezsin. Çözümler:
- Partial unique index: `CREATE UNIQUE INDEX ON products (sku) WHERE enabled = TRUE` (Postgres)
- SKU rename on soft-delete: `sku = sku || '_deleted_' || timestamp`

---

## 7. Header-trust security model

`HeaderAuthenticationFilter` (`OncePerRequestFilter`):
```java
String userId = req.getHeader("X-User-Id");
String role = req.getHeader("X-User-Role");
if (userId != null && role != null) {
    var auth = new UsernamePasswordAuthenticationToken(
        Long.valueOf(userId), null,
        List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    SecurityContextHolder.getContext().setAuthentication(auth);
}
```

Gateway JWT'yi parse etmiş, header eklemiş. Product service header'a güveniyor, JWT yeniden parse etmiyor.

**Avantaj:** Az kod, hızlı, JWT secret distribution gereksiz.

**Risk:** İç ağ compromise olursa saldırgan header forge edebilir.

**Mitigation:**
- Gateway public (GET) request'lerde `X-User-*` header'ı **strip** ediyor → client'tan gelmiş forgery temizlenir
- Production'da Service Mesh mTLS (Istio) → service-to-service mutually authenticated

**Mülakat:** *Production'da JWT'yi her serviste doğrulamalı mıyım?*
> Trade-off. JWT-everywhere = defense in depth ama ekstra CPU + secret distribution. Header-trust + Service Mesh mTLS = production-grade. Bizim ölçek (öğrenme/portfolio) header-trust + dökümantasyon "production'da Istio veya signed-headers kullanırım" yeterli.

---

## 8. `@PreAuthorize` vs `@Secured`

```java
@PostMapping
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<...> create(...) { ... }
```

| | `@Secured` | `@PreAuthorize` |
|---|---|---|
| Activation | `@EnableMethodSecurity(securedEnabled=true)` | `@EnableMethodSecurity` (default) |
| Syntax | `@Secured("ROLE_ADMIN")` | `@PreAuthorize("hasRole('ADMIN')")` |
| SpEL | ❌ | ✅ Full Spring EL |
| Method args | ❌ | ✅ `#user.id == authentication.principal` |
| Modern | Legacy | Recommended |

`hasRole('ADMIN')` otomatik `ROLE_` prefix ekler. `hasAuthority('ROLE_ADMIN')` explicit. Authority'lerimiz `ROLE_<role>` ile başlıyor → `hasRole` çalışır.

---

## 9. Sınır kontrolleri (controller defense)

Pagination cap:
```java
int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
```
- `Math.max(size, 1)` → negatif ya da 0 saldırı engellenir
- `Math.min(..., 100)` → 1M page size DDoS engellenir

Sort whitelist:
```java
String safeSortBy = ALLOWED_SORT.contains(sortBy) ? sortBy : "id";
```

Page negatif:
```java
PageRequest.of(Math.max(page, 0), ...)
```

**Mülakat:** *İnput validation katmanları?*
> 1. **DTO `@NotBlank @Size`** — Bean Validation, GlobalExceptionHandler 400
> 2. **Controller cap'leri** — pagination, sort whitelist
> 3. **Service domain validation** — business rule (örn. stok yeter mi)
> 4. **DB constraint** — UNIQUE, NOT NULL, CHECK; son backstop

---

## 10. Flyway migration discipline

**V1__init_products.sql** schema yarattı. Sonra `price_currency CHAR(3)` kullanmıştım, Hibernate bu kolonu `bpchar` olarak okuyup `varchar(3)` ile karşılaştırma yapamadı (validation hatası). **V2** seed data yazdım (V1 değiştirmeden), **V3** alter migration ile CHAR'ı VARCHAR'a çevirdim. Sonradan V1'i temizleyerek tek seferde VARCHAR yapıldı (recovery sürecinde).

**Mülakat:** *Production'da V1'i değiştirmek?*
> Asla. Flyway checksum doğrular. V1 prod'da çalışmışsa hash kayıtlı; değiştirilmiş V1 yeniden deploy edilirse `FlywayValidateError`. Doğru pratik: yeni V_N migration ekle. Sadece feature branch'te merge'den önce squash kabul edilebilir.

**`spring.flyway.enabled=true`** + **`spring.jpa.hibernate.ddl-auto=validate`** kombosu = "schema'yı Flyway yönetir, Hibernate sadece doğrulama yapar". Production-correct ayar.

---

## 11. `BigDecimal` precision/scale

`price_amount NUMERIC(12,2)` → `BigDecimal` ile mapping:
```java
@Column(name = "price_amount", nullable = false, precision = 12, scale = 2)
private BigDecimal priceAmount;
```

- `precision = 12` → 12 hane
- `scale = 2` → kuruş hassasiyeti

**Niye `double` değil?** Floating point precision kaybı. `0.1 + 0.2 == 0.3` Java'da `false`. Para hesaplamalarında **kabul edilemez**. Always `BigDecimal`.

**Mülakat:** *Para için neden BigDecimal?*
> IEEE 754 floating point binary representation rational sayıları kaybeder. `BigDecimal` arbitrary precision. Toplama/çarpma `add()`, `multiply(BigDecimal.valueOf(qty))` ile. **`new BigDecimal(0.1)` yapma** — double'dan inşa eder, kaybeder. Always `new BigDecimal("0.1")` veya `BigDecimal.valueOf(0.1)`.

---

## 12. Mülakatta Faz 2 hikayesi

> Spring Data JPA Specification API ile filter composition kullanarak public catalog endpoint'i tasarladım. `Product → Category` lazy ilişkisi `@EntityGraph` ile listing path'inde tek SQL'e indirildi — Testcontainers ile gerçek Postgres'te `em.clear()` sonrası category access'in çalıştığını assert ederek N+1 olmadığını kanıtladım. Pagination Spring Data offset, sort columns whitelist'le SQL injection'a karşı güvenli, page size 100'de cap'li. Soft-delete `enabled=false` ile (hard delete order history'yi bozar). Security gateway-supplied `X-User-Id`/`X-User-Role` header'larına güveniyor (production'da service mesh mTLS gerekir); admin endpoint'leri `@PreAuthorize("hasRole('ADMIN')")` ile guard. Flyway V1+V2 seed data, MapStruct ile Product → ProductResponse mapping. Para `BigDecimal` precision 12 scale 2.

---

## 13. Mülakat soruları (kısa liste)

1. N+1 problem nedir, Specification API + `@EntityGraph` ile nasıl çözdün, nasıl test ettin?
2. `@EntityGraph` vs JOIN FETCH — pagination'da neden farklı?
3. Specification API vs Criteria vs `@Query`?
4. Offset vs cursor pagination, ne zaman hangisi?
5. Sort whitelist neden injection defense?
6. `open-in-view: false` neden?
7. Soft-delete unique constraint problemi nasıl çözülür?
8. Header-trust vs JWT-everywhere — production'da ne yaparsın?
9. `@PreAuthorize` vs `@Secured`?
10. Flyway production'da V1 değiştirilebilir mi?
11. Para neden `BigDecimal`, neden `double` değil?
12. Database-per-service principle — niye productdb ayrı?
