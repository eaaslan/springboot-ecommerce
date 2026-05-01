# Phase 2 — Product Service Implementation Plan

Spec: `docs/superpowers/specs/2026-04-29-phase-2-product-service-design.md`

## Tasks (compact, all delivered)

1. **docker-compose init.sql** provisions `productdb` + `product` user on fresh volume.
2. **Module skeleton** `services/product-service` (port 8082): web, jpa, security, validation, actuator, config-client, eureka-client, flyway, postgresql, mapstruct, lombok, springdoc 2.6, testcontainers, h2, json-path.
3. **Application class** `@EnableDiscoveryClient`, `scanBasePackages` includes common. `application.yml` config-import. logback template.
4. **Config Server entries** `product-service.yml` (port, datasource, JPA validate, open-in-view false, Flyway), dev override.
5. **Flyway V1** schema (categories, products with VARCHAR(3) currency to match Hibernate validation), V2 seed (5 cats × 20 products).
6. **Entities** `Category`, `Product` with `@ManyToOne(LAZY)`, audit fields, `@Version`. Lombok discipline (no `@Data`, `@EqualsAndHashCode(of="id")`).
7. **Repositories**: `JpaSpecificationExecutor<Product>`; override `findAll(Specification, Pageable)` with `@EntityGraph(attributePaths="category")`. `findWithCategoryById` for detail. `ProductSpecifications` static helpers (nameContains, hasCategory, priceBetween, inStock, enabledOnly).
8. **DTOs (records)** `ProductResponse`, `CategoryResponse`, `ProductCreateRequest`/`UpdateRequest` with Bean Validation, `ProductFilter`, custom `PageResponse<T>` envelope, MapStruct `ProductMapper`.
9. **ProductService** — list (filter spec composition), getById (entity graph), listCategories, create (SKU duplicate check), update (patch non-null), softDelete.
10. **ProductController** — sort whitelist, page-size cap (100), `@PreAuthorize("hasRole('ADMIN')")` on writes.
11. **HeaderAuthenticationFilter** — read `X-User-Id` + `X-User-Role`, build Authentication.
12. **SecurityConfig** — GET permitAll on products, others authenticated, custom 401/403 handlers.
13. **GlobalExceptionHandler** maps BusinessException + validation to `ErrorResponse`.
14. **OpenApiConfig** with bearer scheme.
15. **Smoke test** with H2 fallback.
16. **ProductRepositoryTest** (Testcontainers) — seed loads, pagination, specification AND filters, entity graph eager load.
17. **ProductFlowIntegrationTest** (Testcontainers + MockMvc) — public list, filter, getById, categories, anon→401, USER→403, ADMIN→201/204, duplicate SKU→409.
18. **API Gateway updates** — `/api/products/**` route, `GET` bypass JWT (public catalog), strip `X-User-*` on public path to prevent forgery.

## Acceptance

`mvn clean verify` (postgres up + productdb provisioned) → BUILD SUCCESS.
