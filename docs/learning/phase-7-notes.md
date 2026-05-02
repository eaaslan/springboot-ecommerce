# Phase 7 — Kafka + Outbox Pattern

> Hedef: Phase 6'daki **dual-write açığını kapatmak**. Order CONFIRMED ve broker publish iki farklı resource'a yazıyordu — ikisi atomik değildi. Outbox pattern ile çözüm: event'i DB'ye yaz (transactional), ayrı bir relay broker'a publish etsin.

---

## 1. Dual-Write Problem — Phase 6'da Ne Eksikti?

```java
// PHASE 6 (eksik):
order.setStatus(CONFIRMED);
orderRepository.save(order);          // ✅ DB'ye commit
rabbit.convertAndSend(event);         // ❌ broker down ise event KAYIP
// → Kullanıcıya "order CONFIRMED" denildi ama notification gitmedi
```

İki ayrı sistemı (DB + broker) tek bir transaction'a bağlayamazsın. Bunu çözmek için 3 yaklaşım var:

| Yaklaşım | Açıklama | Trade-off |
|---|---|---|
| **2PC (XA)** | Distributed transaction protocol | Bloklayıcı, network hassas, modern broker'lar desteklemez |
| **Outbox pattern** | DB'ye event row yaz, ayrı poller publish etsin | Basit, robust — bizim seçimimiz |
| **CDC (Debezium)** | DB WAL'ı stream et, otomatik publish | Production-grade, daha karmaşık |

> **Mülakat sorusu:** "Dual-write problemini nasıl çözersin?"
> Cevap: Tek bir resource'a (DB) yaz. Outbox pattern'le aynı transaction'da event row insert et, sonra ayrı bir relay process broker'a yollasın. DB commit garanti = event publish garanti (eventually).

---

## 2. Outbox Pattern — Akış

```
                                       ┌──────────────────────────────┐
   Order Service @Transactional        │ orders                       │
   ─────────────────────────────────▶ │   id, status=CONFIRMED        │
   step 6: aynı TX içinde              │ outbox_events                 │
                                       │   eventId, payload, status=  │
                                       │   PENDING                    │
                                       └──────────────┬───────────────┘
                                                      │ COMMIT (atomic)
                                                      ▼
                                   ╔══════════════════════════════════╗
                                   ║ DB durumu: order CONFIRMED +     ║
                                   ║   outbox row PENDING — atomik    ║
                                   ╚══════════════════════════════════╝

                                                      │
                                                      │ @Scheduled poll (1s)
                                                      ▼
                                       ┌──────────────────────────────┐
                                       │ OutboxRelay                  │
                                       │   findTop50ByStatus=PENDING  │
                                       │   for each:                  │
                                       │     kafka.send(...)          │
                                       │     row.status=PUBLISHED     │
                                       │     ya da FAILED+attempts++  │
                                       └──────────────┬───────────────┘
                                                      │
                                                      ▼
                                       ┌──────────────────────────────┐
                                       │ Kafka topic: order.confirmed │
                                       └──────────────┬───────────────┘
                                                      │ @KafkaListener
                                                      ▼
                                       ┌──────────────────────────────┐
                                       │ Notification Service         │
                                       │   handleOrderConfirmed       │
                                       │   (idempotent dedup'lu)      │
                                       └──────────────────────────────┘
```

### Anahtar Garantiler
1. **Order CONFIRMED ⇒ outbox row var** (aynı TX, atomicity by DB)
2. **Outbox row var ⇒ eventually published** (relay sürekli çalışır, retry eder)
3. **Aynı event N kez gelse de bir kez işlenir** (notification-service'te `processed_events` PK)

Sonuç: **effectively exactly-once.** RabbitMQ + Kafka at-least-once + app-level idempotency.

---

## 3. `outbox_events` Tablosu

```sql
CREATE TABLE outbox_events (
    id              BIGSERIAL PRIMARY KEY,
    event_id        VARCHAR(36) NOT NULL UNIQUE,
    aggregate_type  VARCHAR(80) NOT NULL,        -- 'ORDER'
    aggregate_id    VARCHAR(80) NOT NULL,        -- order id
    event_type      VARCHAR(80) NOT NULL,        -- 'ORDER_CONFIRMED'
    payload         TEXT NOT NULL,               -- JSON
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts        INT NOT NULL DEFAULT 0,
    last_error      VARCHAR(500),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ
);

-- Performans: PENDING rows için partial index
CREATE INDEX idx_outbox_pending ON outbox_events(status, created_at)
    WHERE status = 'PENDING';
```

**Partial index trick:** Tablo büyüyecek (yıllarca PUBLISHED row birikir), ama poller sadece PENDING'i sorguluyor. Partial index sadece PENDING satırlarını içerir — sorgu çok hızlı kalır. Production'da PUBLISHED row'lar için ayrı archival/retention job ekle.

---

## 4. OutboxRelay — Scheduled Poller

```java
@Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:1000}")
@Transactional
public void publishPending() {
  List<OutboxEvent> batch = repo.findTop50ByStatusOrderByIdAsc(PENDING);
  for (OutboxEvent ev : batch) publishOne(ev);
}

void publishOne(OutboxEvent ev) {
  try {
    kafkaTemplate.send(topic, ev.getAggregateId(), ev.getPayload())
        .get(5, TimeUnit.SECONDS);  // sync within transaction
    ev.setStatus(PUBLISHED);
    ev.setPublishedAt(now());
  } catch (Exception ex) {
    ev.setStatus(FAILED);
    ev.setAttempts(ev.getAttempts() + 1);
    ev.setLastError(truncate(ex.getMessage(), 500));
  }
}
```

### Neden `kafkaTemplate.send().get(5s)`?
Asenkron `send` + `addCallback` daha "Kafka-doğal" ama `@Transactional` boundary commit oluncaya kadar status=PUBLISHED yazılamaz. Sync await TX boundary'sini deterministic yapar.

### Concurrency: birden fazla relay instance varsa?
**Race condition:** iki instance aynı PENDING row'u `findTop50` ile alır → ikisi de Kafka'ya publish eder → consumer'a duplicate gider.
**Çözümler:**
1. `SELECT ... FOR UPDATE SKIP LOCKED` (Postgres 9.5+) — row-level lock
2. ShedLock — distributed lock library
3. Tek-instance design — sadece bir relay node
4. Kafka idempotent producer + consumer dedup (idempotency tablosu zaten var)

MVP'de tek instance varsayıyoruz. Mülakatta "scaling nasıl?" sorulursa SKIP LOCKED + leader election anlat.

---

## 5. Kafka Idempotent Producer

```java
props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
props.put(ProducerConfig.ACKS_CONFIG, "all");
props.put(ProducerConfig.RETRIES_CONFIG, 3);
props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
```

Kafka 0.11+ `enable.idempotence=true` ile aynı producer'ın aynı mesajı network glitch nedeniyle iki kez göndermesi → broker'da deduplikasyon (sequence number + producer ID). Bu **producer→broker** seviyesinde exactly-once.

> **Mülakat sorusu:** "Kafka'da exactly-once nasıl?"
> Cevap: 3 katmanda
> 1. Producer→broker: idempotent producer (sequence-based dedup)
> 2. Broker→consumer: at-least-once + offset commit; consumer tarafı dedup
> 3. End-to-end: transactional producer + consumer (atomic offset commit + write); ya da app-level idempotency tablosu (bizim yaklaşım).

---

## 6. Kafka vs RabbitMQ — Yan Yana

| Özellik | RabbitMQ | Kafka |
|---|---|---|
| Model | Smart broker (routing) | Distributed log (replay) |
| Persistence | Mesaj consume edilince silinir | Retention period — replay edilebilir |
| Ordering | Per-queue | Per-partition |
| Throughput | Orta (binlerce/sn) | Çok yüksek (milyonlarca/sn) |
| Use case | Task queue, RPC, fan-out | Event sourcing, analytics, replay |
| Routing | Exchanges + bindings | Topic + partition |
| Consumer | Push veya pull | Pull (consumer group) |

Bizim use case (notification fan-out): RabbitMQ yeterliydi. Phase 7'de Kafka'yı **Outbox relay için** ekledik çünkü:
- Replay capability — event geçmişine geri dönmek mümkün
- Idempotent producer — relay retry safe
- Schema evolution / Outbox + Kafka klasik pattern

---

## 7. Notification Service — İki Transport, Tek Handler

```java
// AMQP (Phase 6, hâlâ aktif — düşük latency yolu)
@RabbitListener(queues = "${app.rabbit.queue}")
public void onOrderConfirmed(OrderConfirmedEvent event) {
  service.handleOrderConfirmed(event);
}

// Kafka (Phase 7 — Outbox guarantee)
@KafkaListener(topics = "${app.kafka.topics.order-confirmed}",
               containerFactory = "orderConfirmedKafkaListenerContainerFactory")
public void onOrderConfirmedFromKafka(OrderConfirmedEvent event) {
  service.handleOrderConfirmed(event);
}
```

Aynı event hem AMQP hem Kafka'dan gelebilir → `processed_events` table'ı duplicate'leri dedup eder. **Idempotent consumer** olduğu için broker sayısı önemli değil.

> Mülakat trick: "Aynı event'i hem AMQP hem Kafka'dan yollarsan ne olur?" — Cevap: idempotent consumer dedup eder, sorun yok. Bu yüzden migration'larda paralel publish (canary) safe — hem eski hem yeni transport çalışır.

---

## 8. Polling vs CDC (Debezium)

| Polling (bizim) | CDC (Debezium) |
|---|---|
| `SELECT ... WHERE status='PENDING'` her saniye | DB transaction log (WAL) okur, otomatik publish |
| Latency: poll-interval (~1s) | Latency: ~100ms |
| Operational simplicity: 0 ekstra component | Debezium + Kafka Connect cluster |
| Tam kontrol app içinde | Plug-and-play; outbox tablosuna bile gerek yok bazen |

**Ne zaman CDC?**
- Çok yüksek throughput
- Düşük latency kritik
- Outbox tablosu bile istemiyorsan (entity tablosunu doğrudan stream et)

**Ne zaman polling?**
- Basit / öğrenim projesi
- Düşük throughput
- Operational team Debezium istemiyor

> **Mülakat sorusu:** "Outbox + CDC ne anlama gelir?"
> Cevap: Debezium outbox tablosunun WAL'ını okur, Kafka'ya publish eder. App tarafında relay job yazmana gerek kalmaz, ama Debezium operational overhead'ı var.

---

## 9. Hata Senaryoları — Ne Zaman Ne Olur?

### Senaryo A: Order CONFIRMED, Kafka down
- ✅ DB commit oldu (order + outbox row)
- ❌ relay Kafka'ya publish edemiyor → row FAILED + attempts++
- Kafka geri dönünce bir sonraki poll'da PENDING/FAILED row tekrar denenir
- **Manuel olarak FAILED → PENDING:** `UPDATE outbox_events SET status='PENDING' WHERE status='FAILED'`
- Veya retry-FAILED job (production'da scheduled task)

### Senaryo B: Order servisi crash, transaction rollback
- ✅ Hiçbiri commit olmaz (order kalmaz, outbox row kalmaz)
- Saga zaten compensation'larla CANCELLED'a geçirir
- **Beklenen davranış** — atomicity korunur

### Senaryo C: Relay publish etti, ama row update'i fail oldu
- Mesaj Kafka'da ✅
- Outbox row PENDING kaldı ❌
- Bir sonraki poll'da tekrar publish → consumer'a duplicate
- **Çözüm:** consumer idempotent (eventId PK dedup)

### Senaryo D: Birden fazla relay instance race
- İki instance aynı PENDING row'u görür
- İkisi de publish eder → 2 mesaj
- Consumer dedup eder → 1 işlenir
- **Çözüm:** SKIP LOCKED veya leader election

---

## 10. Mülakat Cevapları — Hızlı Referans

**S: Outbox pattern nedir?**
**C:** Distributed transaction yerine: event'i DB'ye yaz (aynı TX entity ile), ayrı bir relay process bu satırları okur ve broker'a publish eder. DB commit = event publish guarantee.

**S: 2PC neden kullanmadın?**
**C:** Bloklayıcı, network hassas, modern broker'lar (Kafka, RabbitMQ) XA desteklemez. Outbox eventual consistency'yi tercih eder ama operationally çok daha sağlam.

**S: At-least-once + idempotency = exactly-once denmesinin sebebi?**
**C:** Broker bir mesajı N kez yollarsa, app-level idempotency table o mesajı sadece bir kez işler. Net etki kullanıcı için exactly-once gibi görünür. "Effectively-once" terimi de kullanılır.

**S: Kafka topic partitioning nasıl çalışır?**
**C:** Topic N partition'a bölünür. Mesaj key'ine göre partition seçilir (default: hash). Aynı key aynı partition'a gider — order guarantee per partition. Consumer group'ta her partition tek bir consumer'a bind olur. Scale = partition count + consumer count.

**S: Outbox tablosu büyürse ne yaparsın?**
**C:** PUBLISHED row'lar için retention. Scheduled job: `DELETE WHERE status='PUBLISHED' AND published_at < now() - interval '7 days'`. Veya partition by month. Partial index PENDING için zaten optimize.

**S: Broker switching (RabbitMQ → Kafka) live nasıl yaparsın?**
**C:** Paralel consume (canary). Hem RabbitMQ hem Kafka aktif, idempotency dedup eder → güvenli. Test sonrası RabbitMQ producer'ı kapat, sonra consumer'ı kapat.

**S: Schema evolution nasıl?**
**C:** Bizim case JSON. Producer yeni field eklerse consumer ignore eder (Jackson `FAIL_ON_UNKNOWN=false`). Field silmek breaking — versioning gerek (eventType=`ORDER_CONFIRMED_V2`). Production'da schema registry (Confluent) + Avro/Protobuf forward/backward compat kontrolü yapar.

---

## 11. Phase 7 Çıktıları

- **Outbox pattern** — `outbox_events` tablo + `OutboxAppender` (saga'da aynı TX) + `OutboxRelay` (@Scheduled)
- **Kafka KRaft single-node** — docker-compose'da bitnami/kafka:3.7
- **Idempotent producer** — `enable.idempotence=true`, acks=all, retries=3
- **`@KafkaListener`** notification-service'te paralel — AMQP'nin yanında
- **Cross-transport idempotency** — `processed_events` PK her ikisini dedup eder
- **Externalized config** — kafka bootstrap, topic names, outbox poll interval config-server'da
- 3 yeni test (1 OutboxRelayTest happy + fail, 1 KafkaNotificationConsumerTest, OrderServiceTest happy path outbox verify)
- Full reactor `mvn clean verify`: **12 modül SUCCESS** (~1dk)
- Tag: `phase-7-complete`

---

## 12. Sıradaki — Phase 8

**Phase 8 — Observability (Prometheus + Grafana + Zipkin):**
- Micrometer metrics → Prometheus scrape
- Grafana dashboard (RPS, p95, error rate, queue depth)
- OpenTelemetry tracing → Zipkin (saga + outbox + kafka full trace)
- Structured logging + correlation-id propagation
- Health/liveness/readiness probes
- SRE-grade visibility için tüm 12 servisin "shape"i bir bakışta görülecek
