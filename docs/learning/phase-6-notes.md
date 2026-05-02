# Phase 6 — Notification Service (RabbitMQ, async messaging)

> Hedef: Order Service "CONFIRMED" olduğunda **asenkron** olarak notification gönderecek bir pipeline kurmak. Senkron HTTP yerine **message broker** (RabbitMQ) kullanmak — order'ı bekletmeden, decoupled, fail-tolerant bir akış.

---

## 1. Senkron vs Asenkron — Neden RabbitMQ?

Phase 5'te order CONFIRMED olduğunda eğer Feign ile notification-service'i sync çağırsaydık:

❌ Notification-service yavaşsa **kullanıcı bekler**
❌ Notification-service crash ederse **order da fail** olur (cascading failure)
❌ Notification kapasitesi order kapasitesini sınırlar

✅ RabbitMQ ile:
- Order servisi sadece **bir mesaj atar** ve devam eder (~1ms)
- Notification servisi kendi hızında consume eder
- Notification crash olsa mesaj kuyrukta kalır → recover ettiğinde işlenir
- Servisler **decoupled** — biri olmadan diğeri çalışmaya devam eder

> **Mülakat sorusu:** "Sync ile async iletişim arasındaki farkı temporal coupling üzerinden açıkla."
> Cevap: Sync request/response her iki servisin **aynı anda** ayakta olmasını gerektirir (temporal coupling). Async messaging'de producer ve consumer farklı zamanlarda çalışabilir; broker mesajı persist eder. Bu availability açısından kritik.

---

## 2. RabbitMQ Topology

```
                                ┌──────────────────────────┐
   Order Service ──publish──▶  │ exchange: order.events   │
   (publisher)                  │   (topic, durable)       │
                                │ routing key:             │
                                │   order.confirmed        │
                                └──────────┬───────────────┘
                                           │
                                           ▼
                                ┌──────────────────────────┐
                                │ queue:                   │
                                │  notification.order-     │
                                │  confirmed.queue         │
                                │  (durable, x-dlx=...)    │
                                └──────────┬───────────────┘
                                           │  consume (3 retry max)
                                           ▼
                                ┌──────────────────────────┐
                                │  Notification Service    │
                                │  @RabbitListener         │
                                └──────────────────────────┘
                                3x fail edersse ↓
                                ┌──────────────────────────┐
                                │ DLX: order.events.dlx    │
                                │ DLQ: notification...     │
                                │   .queue.dlq             │
                                │ (poison message inspect) │
                                └──────────────────────────┘
```

### Kavramlar
- **Exchange:** producer mesajı buraya yollar; routing key + binding üzerinden queue'lara yönlendirir.
- **Topic exchange:** routing key pattern'leriyle (örn. `order.*`, `order.#`) eşleşen queue'lara mesaj kopyası yollar.
- **Queue:** mesajların buffer'landığı yer. Durable = broker restart'ında kaybolmaz.
- **Binding:** queue ↔ exchange ilişkisini ve hangi routing key'le eşleştiğini tanımlar.
- **DLX (Dead-Letter Exchange):** retry'ler tükendikten sonra mesajın sürgün edildiği "oraya gitsin de gözüm görmesin" exchange'i.
- **DLQ (Dead-Letter Queue):** DLX'e bağlı queue; ops ekibi inceleyip ya silinir ya replay edilir.

---

## 3. Delivery Semantics — At-Least-Once

RabbitMQ default'unda **at-least-once** garantisi verir. Yani aynı mesaj birden fazla kez consumer'a gelebilir:

- Consumer mesajı işledi ama ack yollayamadan çöktü → broker tekrar yollar
- Network hiccup → ack lost → broker retry
- Manuel requeue / DLX → kontrol bizde

**Exactly-once** RabbitMQ tek başına vermez. Bunu app-level **idempotency** ile sağlarız.

### Bizim çözümümüz: `processed_events` tablosu

```sql
CREATE TABLE processed_events (
    event_id     VARCHAR(36) PRIMARY KEY,    -- UUID
    event_type   VARCHAR(80) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

Consumer akışı:

```java
@Transactional
public boolean handleOrderConfirmed(OrderConfirmedEvent event) {
  if (processedRepo.existsById(event.eventId())) {
    return false;  // duplicate → skip ve ack
  }
  try {
    processedRepo.save(new ProcessedEvent(event.eventId(), "ORDER_CONFIRMED"));
  } catch (DataIntegrityViolationException dup) {
    // race-condition: 2 consumer aynı anda existsById yaptı
    return false;  // diğer consumer kazandı
  }
  notificationRepo.save(...);  // gerçek iş
  return true;
}
```

Önemli detaylar:
- `processedRepo.save()` ve `notificationRepo.save()` **aynı transaction'da**. Biri fail olursa hiçbiri persist olmaz.
- `existsById` check'i **race-safe değil** tek başına — yarış durumu için catch DataIntegrityViolationException şart.

> **Mülakat sorusu:** "Exactly-once nasıl sağlanır?"
> Cevap: Broker tarafında imkansıza yakın (FLP impossibility). Pratikte at-least-once + idempotent consumer = effectively exactly-once. Bizim case: event_id (producer'da UUID), consumer'da PK constraint, transaction sınırı.

---

## 4. DLQ Pattern — Poison Message Defansı

Eğer mesaj **sürekli fail** ediyorsa (örneğin event şeması bozuk, deserialization patlıyor) ve biz sonsuza kadar retry edersek:
- Queue tıkanır
- CPU/network gereksiz tüketir
- Diğer mesajların hızı düşer

**Çözüm:** retry sayısı bir limite gelince DLX'e gönder. DLQ'da incele, manuel müdahale et.

### Spring AMQP retry

```yaml
spring:
  rabbitmq:
    listener:
      simple:
        retry:
          enabled: true
          initial-interval: 1s
          max-attempts: 3
          multiplier: 2          # exponential backoff: 1s → 2s → 4s
        default-requeue-rejected: false   # fail edince requeue YOK, DLX'e gitsin
```

Queue tanımı:
```java
QueueBuilder.durable(queueName)
  .withArguments(Map.of(
      "x-dead-letter-exchange", dlxName,
      "x-dead-letter-routing-key", routingKey))
  .build();
```

> **Mülakat sorusu:** "Retry'ı queue içinde mi consumer'da mı yaparsın?"
> Cevap: Bizim case'de **consumer-local retry** (Spring RetryTemplate) — fast retries. Buna ek olarak **timed redelivery** istersen `x-message-ttl` + DLX = "delayed retry queue" pattern'i kurabilirsin (önce DLQ'ya, TTL bitince ana queue'ya geri).

---

## 5. Best-Effort Publish — Order Service Tarafı

Phase 6'da publisher'ı saga'ya sıkı bağlamadık:

```java
public void publishOrderConfirmed(Order order) {
  try {
    rabbit.convertAndSend(exchange, routingKey, event);
  } catch (AmqpException ex) {
    log.error("Failed to publish — message lost (Outbox in Phase 7)", ex);
    // ⚠️ order rollback YOK — order CONFIRMED kalıyor, event kayboldu
  }
}
```

**Trade-off:**
- ✅ RabbitMQ down olsa order yine başarılı olur (availability)
- ❌ Eğer order DB commit oldu ve RabbitMQ tam o anda öldü → **event kaybı**

**Phase 7'de Outbox pattern bu açığı kapatacak:**
1. Order'ı CONFIRMED yaparken AYNI transaction'da `outbox_events` tablosuna event row'u yaz.
2. Ayrı bir poller (Debezium veya scheduled task) bu tabloyu okuyup RabbitMQ/Kafka'ya yollar.
3. Yollandıktan sonra row'u silinir / processed işaretler.

Bu sayede DB commit ile event publish **atomik** olur — ya ikisi de olur ya hiçbiri.

> **Mülakat sorusu:** "Dual write problemini nasıl çözersin?"
> Cevap: Outbox pattern. DB ve broker'a aynı anda yazmak = atomicity yok. Outbox'la her şey DB'ye yazılır (ACID), sonra ayrık process broker'a publish eder.

---

## 6. Message Contract — Shared Event Record

Producer + consumer aynı schema'yı bilmeli. `shared/common/event/OrderConfirmedEvent.java` ile tek noktada tanımlı:

```java
public record OrderConfirmedEvent(
    String eventId,
    Long orderId,
    Long userId,
    BigDecimal totalAmount,
    String currency,
    Instant occurredAt) {}
```

Hem `order-service` hem `notification-service` `common` modülüne bağımlı. Schema değişirse her ikisi yeniden compile edilir.

> **Mülakat dikkat:** Production'da event schema'ları için **schema registry** (Confluent Schema Registry, AsyncAPI) tercih edilir — version compatibility (forward/backward) kontrolü merkezi. Bu phase'de basit tuttuk.

### Jackson + Java 21 records
`Jackson2JsonMessageConverter` + `JavaTimeModule` `Instant` serialization için şart. Yoksa `Instant` long timestamp'a serialize olur, deserialize patlar.

---

## 7. Notification Audit Ledger

`notifications` tablosu = "ne yaptık, kime, ne zaman, başarılı mıydı":

| Kolon | Anlam |
|---|---|
| `event_id` | hangi event'ten geldi (idempotency cross-ref) |
| `order_id`, `user_id` | denormalize key'ler — query kolaylığı |
| `channel` | EMAIL / SMS (extension point) |
| `status` | SENT / FAILED |
| `payload` | tam render edilmiş email metni — debug için |
| `failure_reason` | FAILED ise sebep |

**Neden tutulur?**
- Müşteri "email almadım" derse → tabloya bakar "status=SENT, sent_at=..." görürsün → spam'e düştü desin.
- Compliance (KVKK, GDPR): hangi iletişimi yaptığını ispatlama.
- Retry / replay için baseline.

---

## 8. Test Stratejisi

### Unit (Mockito) — `NotificationServiceTest`
- **freshEventPersistsProcessedEventAndNotification** — ilk kez gelen event normal işleniyor
- **duplicateEventByExistsIsSkipped** — `existsById=true` → noop
- **raceConditionDuplicateInsertIsSwallowedAsAlreadyHandled** — `DataIntegrityViolationException` yakalanıp noop

### Smoke
`@SpringBootTest` + `auto-startup=false` (RabbitMQ olmadan context up).

### Integration (deferred)
Testcontainers RabbitMQ ile gerçek pub/sub testi → Phase 7'de ekleyeceğiz (Outbox testi için zaten lazım).

---

## 9. RabbitMQ Management UI

`docker compose up -d rabbitmq` sonra: http://localhost:15672 (guest/guest)

Faydalı tab'lar:
- **Queues:** mesaj sayısı, throughput, consumer sayısı
- **Exchanges:** binding'ler doğru mu kontrol
- **Queues → Get message:** manuel ack/nack, payload preview
- **DLQ inspect:** poison message'ı buradan görürsün

---

## 10. Mülakat Cevapları — Hızlı Referans

**S: RabbitMQ vs Kafka?**
**C:** RabbitMQ akıllı broker, dumb consumer (routing/filter broker'da). Kafka dumb broker, akıllı consumer (replay, partition assignment client'ta). RabbitMQ task queue / RPC için ideal. Kafka event streaming, replay, high throughput. Bizim case (notification fan-out) için RabbitMQ yeterli; Phase 7'de Kafka'ya geçeceğiz çünkü Outbox + replay pattern'i Kafka'da daha temiz.

**S: Idempotent consumer nasıl yazılır?**
**C:** Her event'e unique id (UUID) ver, consumer'da PK constraint'li bir "processed" tablosu tut, transaction içinde önce save sonra business logic. Race condition için DataIntegrityViolation catch.

**S: DLQ'da mesaj birikti, ne yaparsın?**
**C:** Önce neden fail ettiğini anla (deserialization mı, downstream mı). Düzelt. Sonra DLQ'yu ana queue'ya replay et (manuel veya replay endpoint). Replay öncesi idempotency garanti — `processed_events` zaten dedup eder.

**S: Consumer retry bitti, mesaj DLQ'ya gitti, ama bu mesaj normalde işlenmesi gereken bir mesajdı (transient failure). Ne yaparsın?**
**C:** DLQ'dan replay. Ya manuel (RabbitMQ UI), ya admin endpoint, ya scheduled "DLQ drainer". Önemli olan retry/backoff doğru ayarlamak — transient hata için 3 retry yeter, persistent için DLQ'ya hızlıca düşmek mantıklı.

**S: Order servisi event publish ederken broker down ise?**
**C:** Bugün best-effort: log + devam. **Veri kaybı riski.** Çözüm: Outbox pattern (Phase 7) — event row DB'ye yazılır, ayrı poller publish eder. DB commit'i ile event "atomik".

**S: Aynı event'i iki notification-service instance aynı anda alırsa?**
**C:** RabbitMQ default'unda her queue'dan bir consumer alır (round-robin / fair dispatch). Aynı mesajı iki instance almaz — *bir* instance alır. Ama at-least-once nedeniyle aynı eventId iki kez yollanırsa idempotency dedup eder.

**S: Asenkron mimaride latency nasıl ölçülür?**
**C:** End-to-end: order CONFIRMED time → notification sent time. RabbitMQ message timestamp + Notification.sent_at karşılaştırması. Gözleme için Phase 8'de Prometheus metrics (queue depth, consumer rate) + Zipkin tracing (her message'a trace-id propagate).

---

## 11. Phase 6 Çıktıları

- **Notification service** (8087) — 12 modül oldu (10 → 12)
- **RabbitMQ 3-management** docker-compose'a eklendi (5672 amqp + 15672 UI)
- **Topic exchange + DLX/DLQ** topology Java config ile tanımlı
- **Idempotent consumer** (`processed_events` PK + race-safe catch)
- **Audit ledger** (`notifications` tablosu)
- **Best-effort publisher** order-service'te (Outbox Phase 7'ye işaretli TODO)
- **Shared event** `OrderConfirmedEvent` `common` modülüne taşındı
- **Externalized config** (rabbit URL, queue/exchange names config-server'da)
- 4 unit + smoke test → 12 modül `mvn clean verify` SUCCESS
- **Tag:** `phase-6-complete`

---

## 12. Sıradaki — Phase 7

**Phase 7 — Event bus (Kafka, Outbox pattern):**
- Outbox tablosu order-service DB'sinde
- Debezium veya Spring Integration ile relay
- Kafka topic'lere publish (RabbitMQ paralelinde veya yerine)
- Replay capability (event sourcing'in kuzeni)
- Phase 6'daki "best-effort lost-message" açığını kapatma
