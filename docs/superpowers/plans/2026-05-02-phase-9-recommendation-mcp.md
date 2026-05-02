# Phase 9 — Recommendation + MCP Implementation Plan

**Goal:** Add 13th module: recommendation-service with REST API + MCP server (Spring AI). Content-based scoring against product-service via Feign.

**Architecture:** Stateless service, no DB. MCP server starter exposes `@Tool` methods at `/mcp`. Same business methods power REST controller.

**Tech Stack:** Spring AI 1.0 (MCP server starter), Feign, Micrometer.

---

## Tasks

### P9.T1 — Spec + plan + module skeleton
- Files: spec, plan, root pom (add module), `services/recommendation-service/pom.xml` (skeleton), `RecommendationServiceApplication.java`, `application.yml`
- Commit: `chore(infra): recommendation-service module skeleton + phase-9 spec/plan`

### P9.T2 — Spring AI MCP starter
- Add `spring-ai-bom` to dependencyManagement (root pom)
- Add `spring-ai-starter-mcp-server-webmvc` + observability deps to recommendation-service pom
- Commit: `feat(recommendation-service): Spring AI MCP server (webmvc transport) dependencies`

### P9.T3 — ProductClient + DTO
- Files: `ProductClient.java` (Feign), `ProductSummary.java`
- `@FeignClient(name="product-service")` with `GET /api/products` (page) + `GET /api/products/{id}`
- Commit: `feat(recommendation-service): Feign ProductClient + ProductSummary DTO`

### P9.T4 — RecommendationService
- Files: `RecommendationService.java`, `dto/RecommendationItem.java`
- Methods: `similarProducts(productId, k)`, `recommendForUser(userId, k)`, `searchProducts(query, limit)`
- Pure Java content-based scoring (Jaccard tokens + price proximity + category match)
- Commit: `feat(recommendation-service): content-based scoring (Jaccard + price + category)`

### P9.T5 — REST controller + exception + OpenApi
- Files: `RecommendationController.java`, `GlobalExceptionHandler.java`, `OpenApiConfig.java`
- Endpoints: `GET /api/recommendations/products/{id}/similar`, `GET /api/recommendations/users/{id}`, `GET /api/recommendations/search?q=`
- Commit: `feat(recommendation-service): REST controller + exception handler + OpenApi`

### P9.T6 — MCP @Tool methods
- File: `mcp/ProductMcpTools.java`
- Three tools: similar_products, recommend_for_user, search_products
- Wire `ToolCallbackProvider` bean in main app or auto-detect via `@Tool`
- Commit: `feat(recommendation-service): MCP @Tool methods (similar/search/recommend) auto-published at /mcp`

### P9.T7 — Tests + observability
- Unit: `RecommendationServiceTest` (scoring asserts), `ProductMcpToolsTest` (delegation)
- Smoke: `RecommendationServiceApplicationTests` (mock ProductClient)
- Counters: `recommendations.requested{kind}`, `recommendations.duration` Timer
- Commit: `test+obs(recommendation-service): unit tests + business counters`

### P9.T8 — Config Server + gateway route
- `recommendation-service.yml` (port 8088, feign config)
- `api-gateway.yml` route `/api/recommendations/**` and `/mcp/**`
- Commit: `config(recommendation): externalize + gateway routes /api/recommendations/** + /mcp/**`

### P9.T9 — Spotless + verify + README + Turkish notes + tag
- README: row added (port 8088), Phase 9 ✅, MCP try-it
- `docs/learning/phase-9-notes.md`: content vs collaborative, cold start, vector/embedding, RAG, MCP protocol, interview Q&A
- Tag `phase-9-complete`
- Commits: chore + docs

## Verification

1. `mvn clean verify` → 13 modules SUCCESS
2. Tag pushed
