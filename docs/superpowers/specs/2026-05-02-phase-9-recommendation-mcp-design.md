# Phase 9 — Recommendation Service + MCP AI Server Design

## 1. Goal

Add a **Recommendation Service** that exposes both:
1. A **REST API** for the storefront ("similar products", "recommended for user")
2. An **MCP (Model Context Protocol) server** so AI agents (Claude Desktop, Cursor, Claude Code) can call the same recommendation logic as native tools

This is the AI/ML-adjacent phase. We deliberately avoid an external LLM API call (no OpenAI/Anthropic key required) and use **content-based scoring** with pure Java — interview-friendly, deterministic, testable. Vector embeddings + RAG are documented as the production upgrade path.

## 2. Architecture

```
                          ┌─────────────────────────────┐
   REST clients ─────────▶│   Recommendation Service    │
   /api/recommendations   │      (port 8088)            │
                          │                             │
   AI agents ────────────▶│  /mcp  (SSE/HTTP transport) │
   (Claude Desktop,       │  @Tool methods auto-listed  │
    Cursor, Claude Code)  │                             │
                          │  RecommendationService      │
                          │   ├ content-based scorer    │
                          │   └ ProductClient (Feign)   │
                          └────────────┬────────────────┘
                                       │ Feign lb://product-service
                                       ▼
                          ┌─────────────────────────────┐
                          │     Product Service          │
                          │     (port 8082)              │
                          └─────────────────────────────┘
```

## 3. Recommendation Algorithm — Content-Based Scoring

For "products similar to product X":
1. Fetch X (target).
2. Fetch a **candidate pool** (all enabled products, paged — capped to e.g. 200 to keep MVP simple).
3. For each candidate Y ≠ X compute `score(X, Y)`:
   - **Category match:** +1.0 if same category, 0 else
   - **Name token overlap (Jaccard):** `|tokens(X) ∩ tokens(Y)| / |tokens(X) ∪ tokens(Y)|`, weight 0.6
   - **Price proximity:** `1 / (1 + |log(priceX) - log(priceY)|)`, weight 0.4
4. Sort by score desc, return top **k**.

Tokenization: lowercase, split on non-letters, drop length<2 words.

For "recommended for user":
- Phase 9 MVP: return top k products by stock (popularity proxy) — order history join is Phase 10+.
- Annotated as `// TODO Phase 10: replace with order-history-driven scoring` in code.

## 4. Module Layout

```
services/recommendation-service/
├── pom.xml                          # Spring AI MCP server starter + Feign + actuator
└── src/main/java/com/backendguru/recommendationservice/
    ├── RecommendationServiceApplication.java
    ├── client/
    │   ├── ProductClient.java       # Feign to product-service
    │   └── dto/ProductSummary.java
    ├── recommendation/
    │   ├── RecommendationService.java
    │   ├── RecommendationController.java
    │   └── dto/RecommendationResponse.java
    ├── mcp/
    │   └── ProductMcpTools.java     # @Tool annotations
    ├── config/OpenApiConfig.java
    └── exception/GlobalExceptionHandler.java
└── src/main/resources/application.yml
```

No DB. Stateless service.

## 5. MCP Server — `@Tool` Methods

Spring AI MCP server starter scans `@Tool`-annotated public methods on Spring-managed beans and exposes them at `/mcp` (SSE) by default.

```java
@Component
public class ProductMcpTools {

  private final RecommendationService service;

  @Tool(name = "similar_products",
        description = "Find products similar to a given product id")
  public List<RecommendationItem> similar(
      @ToolParam(description = "Product id") long productId,
      @ToolParam(description = "Max results, default 5") Integer k) {
    return service.similarProducts(productId, k == null ? 5 : k);
  }

  @Tool(name = "search_products",
        description = "Free-text search across product names")
  public List<ProductSummary> search(@ToolParam String query, Integer limit) { ... }

  @Tool(name = "recommend_for_user",
        description = "Recommend top-k products for a user (popularity stub)")
  public List<RecommendationItem> forUser(long userId, Integer k) { ... }
}
```

Claude Desktop config snippet (lives in user's `claude_desktop_config.json`):
```json
{
  "mcpServers": {
    "ecommerce": {
      "url": "http://localhost:8088/sse",
      "transport": "sse"
    }
  }
}
```

## 6. Why MCP?

- **Standardized protocol** for AI ↔ tool integration (Anthropic-led, broadly adopted)
- A single server can be consumed by **Claude Desktop**, **Cursor**, **Claude Code**, **Continue**, etc.
- Decouples "what tools exist" from "which IDE/agent calls them"
- Demonstrates familiarity with the modern AI tool-calling ecosystem (resumes/interviews)

## 7. Why Not Vector + LLM Now?

| Option | Trade-off | Decision |
|---|---|---|
| pgvector + sentence-transformers | Real semantic search; needs embedding model + DB extension + retraining | Documented as Phase 9.1 upgrade |
| OpenAI/Anthropic embedding API | External cost; needs API key | Out-of-scope for the open-source repo |
| ChromaDB / Qdrant | Operational overhead | Phase 9.1 |
| Pure content scoring (current) | Deterministic, free, testable, fits MVP | ✅ |

The MCP layer is identical regardless of backing algorithm — that's the point. Future swap is one bean replacement.

## 8. Observability

Custom counters:
- `recommendations.requested{kind="similar|user|search"}` — REST + MCP both increment
- `recommendations.products_scanned` — product pool size per request (cardinality probe)
- `recommendations.duration` — Timer

## 9. Out-of-Scope

| Concern | Why deferred |
|---|---|
| Vector DB / embeddings | Phase 9.1 — needs pgvector or similar |
| Real LLM call (OpenAI/Anthropic) | Cost + key management; we're MCP-server-side |
| Order-history-based collaborative filtering | Needs cross-service data (Phase 10) |
| Caching recommendations | Phase 11 (perf) |
| Rate limiting `/mcp` | Phase 11 |

## 10. Interview Talking Points

1. **MCP** — Anthropic's open standard for AI ↔ tool. Spring AI's MCP server starter auto-wraps `@Tool` methods.
2. **Content-based vs collaborative filtering** — content uses item attributes (our case); collaborative uses user-item interactions (e.g., "users who bought X also bought Y") — needs order history.
3. **Cold start problem** — collaborative fails for new users/items; content-based handles items but not users; hybrid is production-grade.
4. **Vector embeddings** — convert text/products to high-dim vectors; cosine similarity replaces our Jaccard. Sentence-Transformers or OpenAI ada-002 models.
5. **RAG (Retrieval-Augmented Generation)** — vector search finds relevant docs, LLM answers using them as context. We're doing the *retrieval* half deterministically.
6. **Why Spring AI?** — abstracts ChatClient/EmbeddingClient/MCP across providers (OpenAI, Anthropic, Ollama). Switch with one bean.

## 11. Acceptance Criteria

1. `mvn clean verify` succeeds for all 13 modules (recommendation-service added).
2. `curl http://localhost:8088/api/recommendations/products/1/similar?k=3` returns ranked product list.
3. `curl -N http://localhost:8088/sse` returns SSE handshake `event:endpoint data:/mcp/message` (Spring AI MCP server default endpoint, NOT `/mcp`).
4. Through gateway: `curl http://localhost:8080/api/recommendations/products/1/similar`.
5. Tag `phase-9-complete` pushed.
