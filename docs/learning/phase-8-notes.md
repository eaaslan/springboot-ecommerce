# Phase 8 — Observability (Prometheus + Grafana + Zipkin)

> Hedef: 12 modüllük sistemin **shape**'ini bir bakışta görebilmek. Üç sütun (metrics + traces + logs) entegre — production-grade SRE bakış açısı.

---

## 1. Three Pillars of Observability

| Pillar | Sorduğu Soru | Bizim Aracımız |
|---|---|---|
| **Metrics** | "Sistem ne durumda?" — agregasyon, trend | Micrometer → Prometheus → Grafana |
| **Traces** | "Bu istek nereden nereye gitti?" — causal | OpenTelemetry → Zipkin |
| **Logs** | "Tam o anda ne loglandı?" — granular | Logback + JSON encoder + traceId MDC |

Her biri **birbirini tamamlar**. Metric'te spike görürsün → trace'te hangi endpoint'in yavaşladığını bulursun → log'ta o trace_id'yle tam stack-trace alırsın. Spring Cloud bu üçünü `traceId` ile birbirine bağlar.

---

## 2. RED Method — Request-Driven Servisler İçin Standart

Her HTTP servisi için 3 ölçüt kritik:

| Harf | Anlam | Bizim PromQL |
|---|---|---|
| **R**ate | İstek hızı (RPS) | `sum(rate(http_server_requests_seconds_count[1m])) by (application)` |
| **E**rrors | Hata oranı | `sum(rate(http_server_requests_seconds_count{status=~"4..\|5.."}[1m])) by (application)` |
| **D**uration | Latency (p50/p95/p99) | `histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[5m])) by (le, application))` |

> **USE method** (Brendan Gregg) ise resource odaklıdır: Utilization / Saturation / Errors. Database, disk, queue depth için tercih edilir. Bizim Grafana board'unda RED + JVM heap (USE benzeri) panellerinin ikisi var.

---

## 3. Histograms vs Summaries — Neden Histogram?

```yaml
metrics:
  distribution:
    percentiles-histogram:
      http.server.requests: true
```

**Summary:** Servis kendi p95/p99'unu hesaplar, sadece o değeri gönderir. Sorun: aggregator, p95'lerin p95'ini doğru hesaplayamaz (matematiksel olarak yanlış).

**Histogram:** Servis bucket count'ları gönderir (örn. 100ms, 500ms, 1s, 2s buckets). Prometheus `histogram_quantile()` ile **tüm replica'ların buckets'ını topla → quantile hesapla** doğru sonuç verir.

> **Mülakat sorusu:** "Replica'lar arası p95 nasıl hesaplanır?"
> Cevap: Histogram bucket'ları topla, sonra quantile. Pre-computed p95 (summary) toplanamaz — istatistiksel hata.

---

## 4. Tracing — OpenTelemetry → Zipkin

```yaml
tracing:
  sampling:
    probability: 1.0       # dev: tümü; prod: 0.1 ile başla
zipkin:
  tracing:
    endpoint: http://localhost:9411/api/v2/spans
```

**Otomatik instrumentation:** HTTP server, RestTemplate / Feign, JDBC, Kafka, RabbitMQ — Spring Cloud + OTel hepsini sarmal alır.

**W3C `traceparent` header:** Gateway istek aldığında trace başlatır (yeni trace ID), Feign çağrılarında header propagate eder. Service → Service → Kafka → Consumer zincirinde **tek trace** görürsün Zipkin'de.

```
[gateway] → [order-service] → [inventory-service]
                            → [payment-service]
                            → [kafka send]
                            → [notification-service @KafkaListener]
```

> **Mülakat sorusu:** "Trace context propagation nasıl çalışır?"
> Cevap: W3C `traceparent: 00-<traceId>-<parentSpanId>-<flags>`. HTTP header, Kafka record header, RabbitMQ header'a yazılır. Receiver bunu parse eder ve aynı trace altında yeni span açar.

---

## 5. Sampling — Hepsini Saklamak Pahalı

| Strateji | Açıklama | Trade-off |
|---|---|---|
| **Always-on (prob=1.0)** | Her span | Hacim büyük; storage ve network maliyeti |
| **Probability sampling** | %X her trace | Basit ama hata trace'leri de kayıp |
| **Tail sampling** | Tüm error/yavaş trace'leri sakla, kalan %X | Akıllı; ama collector cluster gerekir |
| **Adaptive** | Yüke göre dinamik | En karmaşık; production-grade |

Bizim case: dev'de probability=1.0, prod'da 0.1 (TODO env var).

> **Mülakat sorusu:** "p99 latency ölçmek istiyorum ama sample 0.1. Sorun mu?"
> Cevap: Evet — düşük rate'li endpoint'lerde p99 noisy olur. Tail sampling ile yavaş trace'ler her zaman saklanır, p99 doğru kalır.

---

## 6. Cardinality Bombs — En Büyük Tuzak

Metric tag'i = ayrı time-series. Tag değer kombinasyonu N olursa **N adet time-series** Prometheus'a yazılır.

❌ **Yanlış:**
```java
Counter.builder("api.request").tag("user_id", String.valueOf(userId))
```
1M user → 1M time-series → Prometheus döner, RAM patlar.

✅ **Doğru:**
- Tag'leri **bounded** (sınırlı kardinaliteli) tut: `endpoint`, `status`, `method`, `currency`
- High-cardinality (user_id, order_id, request_id) için **traces + logs** kullan, metric değil

> **Mülakat sorusu:** "Prometheus performansı düşük, ne yaparsın?"
> Cevap: Cardinality audit. `prometheus_tsdb_symbol_table_size_bytes` ve `count by (__name__)({__name__=~".+"})` ile en büyük metric'leri bul, tag'leri azalt.

---

## 7. Pull (Prometheus) vs Push (StatsD/Datadog Agent)

| | Pull (Prometheus) | Push (StatsD) |
|---|---|---|
| Trigger | Prometheus servise gelir | Servis StatsD'ye gönderir |
| Service Discovery | Kubernetes/Eureka SD | Yok — servis adresi bilmek lazım |
| Short-lived jobs | Pushgateway intermediary lazım | Doğal fit |
| Network | Prometheus tek yöne çıkar | Servis broker'a |

Prometheus pull modelinin **biggest win**'i: scrape endpoint'i `/actuator/prometheus` standart, service discovery ile auto-detect, hiç config değiştirmeden yeni instance'lar görülür.

---

## 8. Bizim Custom Business Metrics

```java
// order-service / OrderMetrics
orders.placed{currency="TRY"}                   Counter
orders.cancelled{reason="payment|reservation|commit|unexpected"}  Counter
orders.placeOrder.duration                       Timer (p50,p95,p99)

// order-service / OutboxRelay
outbox.published{eventType="ORDER_CONFIRMED"}    Counter
outbox.failed{eventType="ORDER_CONFIRMED"}       Counter

// notification-service / NotificationService
notifications.sent{channel="EMAIL"}              Counter
notifications.duplicate                          Counter
```

Grafana board'unda business panelleri:
- Orders placed/cancelled rate (saniye başına)
- Outbox published vs failed (oranı = relay sağlığı)
- Notifications sent vs duplicate (idempotency oranı)

`outbox.failed > 0` alarm'ı = relay/kafka sorunu uyarısı. `notifications.duplicate / sent > 0.1` = upstream çift-publish ediyor demektir.

---

## 9. Log Pattern + Trace ID Korelasyonu

```yaml
logging:
  pattern:
    level: "%5p [${spring.application.name},%X{traceId:-},%X{spanId:-}]"
```

Her log satırı şöyle çıkar:
```
INFO  [order-service,a3b9f4c2e8d1,4f7a8b9c] Order 101 CONFIRMED
```

Zipkin'de bir trace açtığında trace ID'yi kopyala → log search'e yapıştır → ilgili tüm satırları bul. **Çok güçlü bir debug yöntemi.**

---

## 10. Mülakat Cevapları — Hızlı Referans

**S: Three pillars nedir?**
**C:** Metrics (aggregate), traces (causal flow), logs (granular). Her biri farklı soruyu cevaplar; observability mature olduğunda üçü trace_id ile bağlanır (correlation).

**S: RED method nedir?**
**C:** Rate (RPS), Errors (error rate), Duration (latency). Request-driven servisler için baseline alarm metrikleri. Tom Wilkie / Weaveworks'ten.

**S: USE method?**
**C:** Utilization (kullanım %), Saturation (queue/wait), Errors. Resource-driven (DB, disk, queue) için Brendan Gregg'in ürünü.

**S: Histogram vs summary?**
**C:** Histogram bucket count'ları aggregator'da `histogram_quantile`'la doğru toplanır. Summary servis-local p95'i gönderir, replica'lar arası p95 hesaplanamaz (math wrong).

**S: Tracing sampling stratejileri?**
**C:** Probability (basit), tail (akıllı, error/slow her zaman tut), adaptive (load-aware). Production'da tail sampling tercih edilir — tüm hatalar saklanır, başarılılar downsampled.

**S: Cardinality bomb nedir?**
**C:** High-cardinality tag (user_id, request_id) metric'e konursa N time-series patlar. Storage/RAM krizi. Çözüm: high-cardinality detayını traces/logs'ta tut, metric'te bounded tag.

**S: Distributed tracing'in temel header'ı?**
**C:** W3C `traceparent` (Spring Cloud 3.x default). Format: `00-<traceId>-<parentId>-<flags>`. B3 (Zipkin native) ve Jaeger formatları da var.

**S: Bir endpoint yavaşladı nasıl debug edersin?**
**C:** 1) Grafana p95 dashboard hangi service yavaş? 2) Zipkin'de o service trace'leri aç, en uzun span hangi downstream call'da? 3) Log search trace_id ile o anki errorlar var mı? 4) DB / Feign client / Kafka producer hangisinde bottleneck.

**S: Cluster scrape yaparken duplicate metric problemi?**
**C:** Aynı service N replica → her birinin scrape'i ayrı `instance` label'ı ile gelir. `sum by (application)` aggregation tüm replica'ları toplar. Eğer instance label'ı boş/aynıysa metrics karışır.

**S: Alert nasıl yaparsın?**
**C:** Prometheus + Alertmanager. Rules YAML'da `expr` (PromQL) + `for: 5m` (kalıcılık) + `severity` label. Alertmanager Slack/PagerDuty'ye route eder. Bizim phase'de doc-only.

---

## 11. Phase 8 Çıktıları

- **3 yeni docker servis:** prometheus (9090), grafana (3000), zipkin (9411)
- **10 servisin her birinde:** micrometer-registry-prometheus, micrometer-tracing-bridge-otel, opentelemetry-exporter-zipkin
- **Default config:** `/actuator/prometheus` exposed, percentile histogram açık, tracing sampling 1.0, zipkin endpoint, log pattern `[app, traceId, spanId]`
- **Provisioned Grafana datasource** (Prometheus + Zipkin) + **dashboard** (RED, JVM heap, business counters — 7 panel)
- **Custom business metrics:** orders.placed/cancelled/duration, outbox.published/failed, notifications.sent/duplicate
- 8 + 5 = 13 test pass (order-service + notification-service); 12 modül `mvn clean verify` SUCCESS (~1dk)
- Türkçe notlar: pillars, RED/USE, histograms, sampling, cardinality, propagation, mülakat Q&A
- Tag: `phase-8-complete`

---

## 12. Sıradaki — Phase 9

**Phase 9 — Recommendation / MCP AI Server:**
- Spring AI ile LLM bazlı ürün öneri servisi
- MCP (Model Context Protocol) server — Claude/Cursor'dan doğrudan ürün arama
- Vector embedding (pgvector veya in-memory)
- "Bu ürüne benzer ne var" / "kullanıcı geçmişine göre öner"
