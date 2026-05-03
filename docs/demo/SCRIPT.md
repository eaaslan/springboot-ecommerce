# 10-dakikalık demo videosu — kayıt scripti

Spring Boot e-commerce mülakat sunumu. Hedef: **9–10 dakika**, hiç klavye yok,
bütün veri prefill, geçişler tek tık.

## Kayıttan ÖNCE

```bash
# 1. Stack'i kaldır + her şeyi seed'le
cd ~/IdeaProjects/springboot-ecommerce
docker compose up --build -d
./docs/demo/prep-demo.sh         # ~30sn, her şeyi hazırlar

# 2. Tarayıcıyı aç (Chrome/Firefox)
#    Aşağıdaki tab'leri ÖNCEDEN aç ve hard reload (Cmd+Shift+R) yap:
#       Tab 1: http://localhost                  (storefront)
#       Tab 2: http://localhost/demo             (login switcher)
#       Tab 3: http://localhost/admin/payouts    (admin payouts — alice'la giriş yaptıktan sonra)
#       Tab 4: http://localhost:3000             (Grafana — admin/admin)
#       Tab 5: http://localhost:9411             (Zipkin)
#       Tab 6: https://github.com/eaaslan/springboot-ecommerce  (GitHub repo)

# 3. Terminal hazırla (kayıt için ayrı pencere)
#    docker compose ps —t {{.Name}}\t{{.Status}} — sağda küçük tut

# 4. (Opsiyonel) Claude Desktop / Claude Code'ı aç → MCP demo için
#    claude mcp add --transport sse ecommerce http://localhost:8088/sse

# 5. Tarayıcı zoom %110, font okunabilir olsun

# 6. Bildirimleri kapat (DND mode)
```

## Kayıt sırasında zaman çizelgesi

> **Önerim:** önce sessiz ekran kaydı çek (klavye/fare hareketleri), sonra
> voice-over ekle. Söyleyeceklerinin metnini bu dosyadan oku.

---

### 0:00 – 0:30 · Açılış (30sn)

**Ekran:** README'nin ilk ekranı (architecture diagram bölümünde scroll).

**Söylenecek:**
> "Merhaba, bu projede production-grade bir e-ticaret platformu kurdum.
> Spring Boot 3, Spring Cloud 2024, 13 microservis. Multi-seller marketplace, distributed saga, Kafka tabanlı event bus, Iyzico ödeme, Prometheus + Grafana ile observability, ve AI agent'leri için MCP server.
> Tek `docker compose up --build` ile sıfırdan ayağa. Şimdi sırayla göstereyim."

**Tıklama:** README → "Architecture at a glance" ASCII diagram'ını 5 saniye bekle.

---

### 0:30 – 2:30 · Müşteri akışı (2dk) — "platform yaşıyor"

**Ekran:** Tab 2 (`/demo`).

**Söylenecek:**
> "Demo paneli — videolar için bir login switcher hazırladım. 3 grup var: admin, satıcılar, alıcılar. Buyer1 olarak giriyorum."

**Tıklama:** "Buyer #1" → "Login & go to /" butonu.

**Ekran:** Storefront, ürün grid'i.

**Söylenecek:**
> "n11 tarzında bir storefront — kart-grid, ürün başına 'Sold by X' satırı var. Buy-box algoritmamız: en düşük `price + shipping × 5` skoru kazanır, n11/Trendyol'un mantığıyla aynı. Burada her ürünün arkasında inventory-service'ten **canlı stok** çekiyoruz, Feign + Resilience4j ile fallback'lı."

**Tıklama:** Bir ürünü seç (ikinci-üçüncü sayfadan, listing'li olan iyi).

**Ekran:** Product detail.

**Söylenecek:**
> "Sağda buy-box: en iyi satıcının fiyatı, sold-by linki, qty seçimi. Aşağıdaki 'Other sellers' panelinde aynı ürünü farklı satıcılar farklı fiyatla satıyor. Ortalama puanı reviews tablosundan otomatik recompute ediyor — yorumları da burada görüyoruz."

**Tıklama:** Qty 1 → "Add to Cart" → Cart.

**Ekran:** Cart sayfası.

**Söylenecek:**
> "Sepette her satırın altında 'Sold by X' var, satırın hangi satıcıyla bağlı olduğunu unutmuyor."

**Tıklama:** Checkout → Submit (kart `4111-1111-1111-1111` zaten dolu).

**Ekran:** Order confirmation → Order detail.

**Söylenecek (kritik kısım, yavaş konuş):**
> "Bu tıklama arkada ciddi bir akış başlattı. **7 adımlı saga**: cart-service'ten cart fetch, kupon validate, order PENDING persist, **inventory-service'te stok rezervasyonu**, **payment-service'ten Iyzico charge** — Iyzico SDK'sıyla gerçek non-3D entegrasyonu var, sandbox key set'liyse gerçek API'ye gidiyor. Sonra inventory commit, **sub-orders split** — her satıcı için ayrı sub-order yaratıyor commission ledger'la, **outbox tablosuna event** yazıyor aynı transaction'da, ve **Kafka'ya publish** ediyor. notification-service Kafka'dan tüketiyor, idempotent dedup'la."

**Ekran:** Order detail'in "Per-seller breakdown" bölümünü göster.

**Söylenecek:**
> "Burada görüyorsunuz: tek bir checkout iki satır oluşturmuş — biri satıcı için, biri platform için. Her birinin commission'ı, payout'u, status'u ayrı tutuluyor."

---

### 2:30 – 4:00 · Marketplace lifecycle (1.5dk) — "n11/Trendyol mantığı"

**Tıklama:** Tab 2 → /demo → "Buyer #1" hala seçili → header'dan "Become a Seller".

**Ekran:** Seller apply page.

**Söylenecek:**
> "Multi-seller marketplace — herkes satıcı olabilir. Demo için 3 hazır şablon var, tıkla forma düşsün. Anadolu Ticaret'i seçiyorum, Submit."

**Tıklama:** "Anadolu Ticaret — Use this draft" → Submit application → dashboard'a düşer (PENDING).

**Söylenecek:**
> "Başvuru PENDING durumda. Şimdi admin tarafına geçiyorum."

**Tıklama:** Tab 2 → /demo → Alice → /admin/products'a düşer.

**Söylenecek:**
> "Admin paneli — Products, Coupons, Sellers, Payouts. Sellers'a giriyorum."

**Tıklama:** Header'dan "Sellers" → /admin/sellers → status filter PENDING.

**Söylenecek:**
> "Anadolu Ticaret PENDING'de. Approve."

**Tıklama:** Approve → status ACTIVE.

**Söylenecek:**
> "Saniyeler içinde aktif satıcı oldu. Artık ürün listeleyebilir, sub-order alabilir."

---

### 4:00 – 5:00 · Sub-orders + payout (1dk) — "para akışı"

**Tıklama:** Header'dan "Payouts" → /admin/payouts.

**Ekran:** Payouts tablosu (prep-demo.sh sayesinde dolu).

**Söylenecek:**
> "Payout ledger. Haftalık batch — admin tarihi seçer, Run payout dersiniz, tüm PENDING sub-order'ları satıcı bazında gruplayıp tek satıra çevirir. **Idempotent**: aynı periode tekrar bastırırsanız yeni satır gelmez, çünkü `(seller_id, period_start, period_end)` unique. Mark Paid butonuyla manual da geçirilebilir — gerçekte Iyzico veya banka API'yle hookladığında otomatik."

**Tıklama:** (Opsiyonel) "Run payout" butonuna bas, "No eligible sub-orders" mesajını göster — idempotency demo.

---

### 5:00 – 6:00 · Saga görselleştirmesi (1dk) — **wow #1**

**Tıklama:** Tab 5 → Zipkin (http://localhost:9411).

**Söylenecek:**
> "Şimdi az önce attığım siparişin trace'ine bakalım. Zipkin'de search."

**Tıklama:** Service name → `api-gateway` → Run query → en son trace'i seç.

**Ekran:** Trace timeline.

**Söylenecek:**
> "Tek bir HTTP isteği 8 servisi geziyor. Gateway → order-service'in saga'sı → cart-service Feign call → inventory-service reservation → payment-service charge → seller-service commission lookup → outbox publisher → kafka producer. Her span'in timing'i var, bottleneck'i tespit etmek için yeterli. **OpenTelemetry + Micrometer Tracing**, gateway'de correlation ID enjekte ediliyor, log'larda da aynı traceId görünüyor."

---

### 6:00 – 7:00 · Observability (1dk) — "production-grade"

**Tıklama:** Tab 4 → Grafana (admin/admin) → "Microservices Overview" dashboard.

**Söylenecek:**
> "Grafana panelinde her servis için **RED metrikleri**: rate, errors, duration. Business counter'lar var: `orders.placed`, `orders.cancelled` reason tag'iyle, `coupon.redeemed`. Prometheus 13 servisten scrape ediyor, retention 15 günlük. Sağ üstte canlı throughput, soldaki panelde error rate, alttaki latency histogramları — production'a hazır gözleme katmanı."

**(Opsiyonel)** RabbitMQ tab'i: http://localhost:15672 (guest/guest) — "kuyruklar burada, DLQ var, idempotent consumer."

---

### 7:00 – 7:45 · AI / MCP (45sn) — **wow #2**

**Tıklama:** Claude Desktop / Claude Code'a geç.

**Söylenecek:**
> "AI agent'leri için MCP server'ımız var, Spring AI ile yazıldı. recommendation-service üzerinde SSE endpoint, üç tool expose ediyor: `searchProducts`, `similarProducts`, `recommendForUser`. Claude bu tool'ları doğrudan çağırabilir."

**Klavyede yaz** (sadece bu kısımda):
```
What headphones do you have under 2000 TRY?
```

**Ekran:** Claude `searchProducts` tool'unu çağırır, sonuçları döner.

**Söylenecek:**
> "Yapay zeka agent'lerinin backend'imize entegre edilebilmesi için tasarladık — REST API'ler MCP tool'larına bind ediliyor. Cursor, Claude Desktop, Claude Code — hepsi bu server'a konuşabiliyor."

---

### 7:45 – 8:45 · Deploy story (1dk) — "tek komut"

**Tıklama:** Terminal aç, README'yi göster.

**Söylenecek:**
> "Sıfırdan klon → `docker compose up --build` → 21 container ayakta. Her şey container'da: Postgres, Redis, RabbitMQ, Kafka, Prometheus, Grafana, Zipkin, 13 Spring servisi, frontend nginx. Frontend Buildx git-context'le başka repo'dan otomatik clone'lanıyor — host'ta JDK veya Node bile gerekmez."

**Tıklama:** Tab 6 → GitHub → Actions sekmesi.

**Söylenecek:**
> "Her main push GHCR'a 14 image basıyor — Jib ile, multi-arch ARM64+AMD64. Slack webhook'u var, success/failure'a notification. Üç compose dosyası var: default'da source'tan build, prod'da GHCR'dan pull, infra-only dev için. AWS Beanstalk için de `aws/` klasöründe RDS+ElastiCache'li deploy bundle hazır."

---

### 8:45 – 9:30 · Phase log (45sn) — "kapsamlı geçmiş"

**Tıklama:** README → Roadmap tablosu.

**Söylenecek:**
> "Proje 13 phase'de gelişti. Phase 0–12: foundation, JWT, catalog, cart, saga, RabbitMQ, Kafka outbox, observability, AI/MCP, reactive layer, idempotency + caching, CI/CD. Phase 13'te marketplace'i V1'den V4'e kademeli ekledim — V1 seller domain, V2 listing-aware cart, V3 sub-orders + commission, V4 reviews + payouts + returns + storefront. Her milestone git tag'ı, geri-dönülebilir branch."

---

### 9:30 – 10:00 · Kapanış (30sn)

**Tıklama:** README üstüne dön.

**Söylenecek:**
> "Test'ler 80+, CI yeşil. README'de tam bring-up rehberi, mimari diyagramı, troubleshooting bölümü var. GitHub'da repo public — link açıklamada. Sorular için müsaitim, teşekkürler."

---

## Zorlukla karşılaşırsan

| Sorun | Hızlı çözüm |
|---|---|
| Tab açıkken sayfa boş | Hard reload (`Cmd+Shift+R`), service worker'ı drop eder |
| Storefront görseller yüklenmiyor | `API_URL=http://localhost node scripts/seed/backfill-images.js` |
| Demo paneli "Invalid credentials" | seed.js çalıştırılmamış: `./docs/demo/prep-demo.sh` |
| Order placement 503 "no instance" | Eureka cache hot değil, 30sn bekle |
| Gateway → seller-service 503 | seller-service yeni restart edildi, 30sn bekle |
| Iyzico key set'liyse ama hata veriyor | sandbox panel'inde test kartı kullan: `5528790000000008` |

## Voice-over taktiği

- **80% script'ten oku**, %20 doğaçlama. Hazır cümleler "ee, şey, hmm" sayısını düşürür.
- "Burada şunu yaptık" yerine "**Burada şu kararı verdim:**" — değer yaratan ifade.
- Code mention'larında her zaman **niye o kararı verdiğini söyle**, ne kullandığını değil. "Saga kullandım" değil, "Distributed transaction'ı **synchronous saga** ile çözdüm çünkü 7 servis arası inventory + payment + commit + outbox aynı request'in compensation path'lerini paylaşmalı."
- Mülakatçı kafanın hangi seviyede olduğunu anlamak istiyor. Tek seviye derinlikten daha derine inme — özet → bir somut detay → bir trade-off.
- 30 saniye → 10 saniye — eğer bir bölüm tıkanırsan voice-over kayıtta kısalt.

## Kayıttan SONRA

```bash
# Stack'i kapat (data tutmak istiyorsan -v koyma)
docker compose down -v
```

İyi şanslar 🎬
