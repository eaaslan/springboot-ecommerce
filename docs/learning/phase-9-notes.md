# Phase 9 — Recommendation Service + MCP AI Server

> Hedef: AI/ML-adjacent bir servis ekledik. **Hem REST API**'den ürün önerisi hizmet eder, **hem de MCP** (Model Context Protocol) server olarak Claude Desktop / Cursor / Claude Code gibi AI ajanlarına araç (tool) olarak servis eder. Vector embedding/LLM API gerektirmeyen, deterministic, mülakatta kolay savunulabilir bir çözüm.

---

## 1. Neden Recommendation?

E-ticaret'te user retention'ı arttıran en güçlü tekniklerden biri. Amazon "buradaki insanlar şunu da aldı", Netflix "izlediklerine benzer", Spotify "Discover Weekly". Hepsi **recommendation engine**.

İki ana aile:

| Yaklaşım | Veri | Avantaj | Cold Start |
|---|---|---|---|
| **Content-based** | Item attributes (kategori, isim, fiyat) | Yeni item için çalışır, açıklanabilir | Yeni user fail |
| **Collaborative filtering** | User-item interactions (kim ne aldı) | Trending/serendipity yakalar | Yeni user + yeni item fail |
| **Hybrid** | İkisi birden | Production-grade | — |

Bizim Phase 9 = **content-based**. Phase 10'da order history geleceği için collaborative ekleyebiliriz.

> **Mülakat sorusu:** "Cold start problem nedir?"
> Cevap: Yeni user veya yeni item için yeterli interaction verisi olmamasıdır. Collaborative filtering bunlar için recommendation üretemez. Çözüm: hybrid model — content-based ile yeni item, popularity baseline ile yeni user'ı handle et, kullanıcı veri biriktirdikçe collaborative'a geç.

---

## 2. Bizim Algoritma

```
score(target, candidate) = 1.0 * categoryMatch
                         + 0.6 * jaccard(nameTokens)
                         + 0.4 * priceProximity
```

### Bileşenler

| Bileşen | Formül | Aralık |
|---|---|---|
| **categoryMatch** | aynı kategori → 1, değilse 0 | 0 veya 1 |
| **jaccard(tokens)** | `\|A ∩ B\| / \|A ∪ B\|` | [0,1] |
| **priceProximity** | `1 / (1 + \|log(p1) - log(p2)\|)` | (0,1] |

Toplam score [0, 2] aralığında. En yüksek **k** tanesini döner.

### Token üretimi
- Lowercase
- Non-letter regex split (`[^\p{L}0-9]+`)
- Length<2 drop

"Wireless Bluetooth-Headphones X1" → `{wireless, bluetooth, headphones, x1}`

> **Mülakat sorusu:** "Neden cosine similarity değil de Jaccard?"
> Cevap: Jaccard set-based, basit metin tokenları için yeterli ve hızlı. Cosine similarity vektör (TF-IDF, embedding) gerektirir. Phase 9 vektör DB istemediğimiz için Jaccard. Vector embedding eklendiğinde cosine similarity'a geçeriz — algoritma değişir, MCP tool surface aynı kalır.

---

## 3. Vector Embeddings — Production Yolu

Modern recommendation = **dense vector embedding** + similarity search.

```
"wireless headphones" → [0.12, -0.45, 0.88, ...]   (768-dim)
"wireless earbuds"     → [0.15, -0.41, 0.82, ...]
cosine(vec1, vec2) = 0.96  → çok benzer
```

| Embedding Source | Spring AI Bean |
|---|---|
| OpenAI ada-002 | `OpenAiEmbeddingClient` |
| Anthropic Voyage | `AnthropicEmbeddingClient` |
| Sentence-Transformers (local) | `TransformersEmbeddingClient` |
| Ollama (local) | `OllamaEmbeddingClient` |

| Vector Store | Açıklama |
|---|---|
| **pgvector** | Postgres extension; aynı DB, ek dependency yok |
| **Qdrant / Chroma / Weaviate** | Dedicated vector DB; HTTP/gRPC |
| **In-memory** | Spring AI `SimpleVectorStore`, prototype için |

Spring AI bir `VectorStore` interface'i sağlar — backend ne olursa olsun aynı API.

```java
// Production'da Phase 9.1
vectorStore.add(Document.from(product.description()));
List<Document> hits = vectorStore.similaritySearch(query);
```

> **Mülakat sorusu:** "Vector search ile keyword search farkı?"
> Cevap: Keyword search (Elasticsearch BM25) literal token match. Vector search semantik — "wireless headphones" sorgusu "Bluetooth earbuds" döndürebilir, çünkü embedding uzayında yakın. Production'da hybrid (BM25 + vector) en iyi sonuç verir.

---

## 4. RAG — Retrieval-Augmented Generation

```
Soru: "Hangi mikrofonu önerirsin podcast için?"
   ↓
1. Sorguyu embed et   → [0.1, 0.4, ...]
2. VectorStore.similaritySearch(query) → top-5 ürün doc
3. LLM'e prompt:
   "Bu ürünleri kullanarak kullanıcı sorusuna cevap ver: <docs>"
4. LLM cevabı: "Shure MV7 önerebilirim çünkü ..."
```

Bizim Phase 9 = **retrieval'in deterministic kısmı**. LLM generate kısmını Spring AI ChatClient'la eklemek bir bean değişikliği.

> **Mülakat sorusu:** "RAG'in faydası ne?"
> Cevap: LLM training cutoff sonrası bilgiyi (yeni ürünler, real-time stok) kullanmasını sağlar. Hallucination'ı azaltır — LLM'e "bu document'lere bak" der, kendi-uydurma yapmasını engeller. Source citation ekleyebilirsin.

---

## 5. MCP — Model Context Protocol

**MCP nedir?** Anthropic'in 2024'te açtığı protokol. AI ajanları (Claude Desktop, Cursor, Claude Code) harici araçları **standart bir şekilde** çağırabilsin diye.

Önceden her AI istemcisi farklı format kullanırdı (OpenAI function calling, Cursor extension API, vs.). MCP ile **bir server, çok client**.

### Bizim case
```
[Claude Desktop] ──HTTP/SSE──▶ http://localhost:8088/mcp
                              │
                              ├ tools.list  → [similarProducts, recommendForUser, searchProducts]
                              └ tools.call  → similarProducts(productId=42, k=5)
```

Spring AI MCP server starter `@Tool` annotated metodları otomatik wraps eder. `ToolCallbackProvider` bean'i `MethodToolCallbackProvider.builder().toolObjects(tools).build()` ile bean tanımı.

### Claude Desktop config (`~/Library/Application Support/Claude/claude_desktop_config.json`)

```json
{
  "mcpServers": {
    "ecommerce": {
      "url": "http://localhost:8088/mcp"
    }
  }
}
```

Restart Claude → "ecommerce" tool'ları otomatik listede.

### `@Tool` ve `@ToolParam`

```java
@Tool(description = "Find products similar to a given product id")
public List<RecommendationItem> similarProducts(
    @ToolParam(description = "Source product id") long productId,
    @ToolParam(description = "Max results, default 5") Integer k) { ... }
```

Description AI ajanı için kritik — model bu açıklamaya göre tool'u ne zaman çağıracağına karar verir. **Vague description = nadir kullanım**, **detaylı description = doğru çağrım**.

> **Mülakat sorusu:** "MCP'nin OpenAI function calling'den farkı?"
> Cevap: OpenAI function calling provider-spesifik (sadece OpenAI modelleri). MCP open standard — bir server'ı Claude, Cursor, Continue, Cline... hepsi tüketebilir. Server-side decoupling.

---

## 6. Spring AI Mimarisi

Spring AI Spring Boot stilinde bir abstraction layer:

| Interface | Implementations |
|---|---|
| `ChatClient` | OpenAI, Anthropic, Mistral, Ollama, Groq, Bedrock |
| `EmbeddingClient` | OpenAI, Cohere, Voyage, Transformers (local) |
| `VectorStore` | pgvector, Qdrant, Chroma, Pinecone, Weaviate, in-memory |
| `ToolCallbackProvider` | Method-based, function-based — MCP layer üzerinde |

Bean swap → provider değişir → kod değişmez. Production-friendly.

> **Mülakat sorusu:** "Provider lock-in nasıl önlenir?"
> Cevap: Spring AI gibi abstraction. Direct OpenAI SDK kullanırsan, API değişirse veya migration gerekirse refactor büyük olur. Spring AI = `@Primary` bean değiştir, bitti.

---

## 7. Test Stratejisi

### Unit (Mockito)
- `RecommendationServiceTest` — 8 test:
  - `jaccardEmptyAndOverlap`, `priceProximityIdenticalIsOne`, `priceProximityFarApart`, `tokensSplit`
  - `similarRanksSameCategoryHigherThanOtherCategory`, `similarThrowsWhenTargetMissing`
  - `recommendForUserPopularityProxyByStock`
  - `searchReturnsItemsWhoseNameOverlapsQuery`, `searchEmptyQuery`

### Smoke
- `RecommendationServiceApplicationTests` — Spring context yüklenir mi (MockBean ProductClient).

Algoritma deterministic olduğu için unit test ile yüksek coverage. LLM-dependent kod olsaydı snapshot/pact testing gerekirdi.

---

## 8. Observability

```java
recommendations.requested{kind="similar|user|search"}    Counter
```

Spring Boot otomatik olarak `http_server_requests_seconds_*` metric'leri export eder — endpoint başına RPS/p95.

Dashboard panelleri:
- Recommendation requests by kind (kim ne çağırıyor?)
- p95 latency (algoritma içi mi yoksa product-service round-trip mi?)
- error rate (Feign call failures yansır)

---

## 9. Mülakat Cevapları — Hızlı Referans

**S: Recommendation algoritmasında hangi yaklaşımı seçtin neden?**
**C:** Content-based. Order history yok (Phase 9), MVP. Jaccard + price proximity + category match — deterministic, açıklanabilir, test'i kolay. Phase 10'da order history gelince hybrid'e geçeceğim.

**S: Production'da nasıl scale edersin?**
**C:** 1) Pre-compute similarity matrix (offline batch) → Redis cache 2) Vector embedding + ANN index (HNSW/FAISS) → milisaniye sub-second latency 3) Cache by productId, TTL 1 saat 4) Trending/popular için ayrı pipeline.

**S: MCP nedir, neden kullandın?**
**C:** Anthropic'in tool calling standardı. AI agents (Claude, Cursor) için. Bir server yazıyorsun, çok client tüketiyor. Provider lock-in yok. Demo için çok güçlü — interview'da "lokal Claude'a bağladım, ürünleri kendisi sorgulayabiliyor" demek somut.

**S: Vector embedding neden kullanmadın?**
**C:** MVP scope. pgvector setup + embedding model deployment (OpenAI key veya local Sentence-Transformers) ek complexity. Algoritma transparent — interview'da matematiği açıklamak kolay. Phase 9.1'de upgrade path documented.

**S: RAG'i nasıl entegre ederdin?**
**C:** 1) Product description'ları VectorStore'a embed et 2) User soruyu embed et 3) similaritySearch top-5 4) ChatClient'a "bu ürünlere göre öner" prompt'u 5) Cevabı stream et. Spring AI'ın 4 satır kodu.

**S: AI hallucination'ı nasıl önlersin?**
**C:** RAG (LLM'e document'ler ver), prompt engineering ("only answer from provided context"), output validation (regex/schema), confidence threshold, source citation requirement.

**S: Token cost nasıl optimize edersin?**
**C:** 1) Embedding cache (aynı text re-compute etme) 2) Prompt template kısa tut 3) Smaller model for routing (Haiku), bigger for synthesis (Sonnet) 4) Streaming response 5) Context truncation (only top-k retrieved docs).

**S: Recommendation evaluation nasıl yapılır?**
**C:** Offline: precision@k, recall@k, NDCG (ranking quality), MAP. Online: A/B test — CTR, conversion rate, revenue per user. Bizim Phase 9'da production data yok; unit test deterministic ranking koruma.

---

## 10. Phase 9 Çıktıları

- **13. modül:** recommendation-service (port 8088)
- **Spring AI 1.0.0-M6** + `spring-ai-mcp-server-webmvc-spring-boot-starter`
- **Content-based scorer** (Jaccard + price proximity + category match)
- **3 REST endpoint:** `/api/recommendations/products/{id}/similar`, `/users/{id}`, `/search`
- **3 MCP `@Tool` method:** similarProducts, recommendForUser, searchProducts — `/mcp` SSE endpoint
- **Gateway routes** `/api/recommendations/**` ve `/mcp/**`; GET'ler JWT bypass
- **10 test pass** (8 unit + 1 smoke + 1 mevcut); 13 modül `mvn clean verify` SUCCESS (~1dk)
- **Türkçe notlar:** content vs collaborative, vector embeddings, RAG, MCP, Spring AI, mülakat Q&A
- **Tag:** `phase-9-complete`

---

## 11. Sıradaki — Phase 10

**Phase 10 — Reactive layer (WebFlux):**
- Bir servisi WebFlux'a taşı (en muhtemelen product-service'in read path'i)
- `Mono`, `Flux`, backpressure
- Reactive PostgreSQL (R2DBC)
- Imperative vs reactive trade-offs
- Mülakatta her zaman gelen "WebFlux ne zaman kullanılır?" sorusuna direkt cevap
