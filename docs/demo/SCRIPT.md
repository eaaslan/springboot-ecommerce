# 10 dakikalık demo videosu — basit anlatım

Mülakat için sade, anlaşılır bir tanıtım. Konuşma dili, kısa cümleler.
**İlk 1.5 dakika proje yapısı**, sonrası canlı demo.

## Kayıttan ÖNCE

```bash
# 1. Proje klasöründe
cd ~/IdeaProjects/springboot-ecommerce

# 2. Stack'i kaldır
docker compose up --build -d

# 3. Demo verisini hazırla (~30sn)
./docs/demo/prep-demo.sh
```

Tarayıcıda şu sekmeleri sırasıyla aç ve `Cmd+Shift+R` ile hard reload yap:

| # | URL | Niçin |
|---|---|---|
| 1 | http://localhost | Site |
| 2 | http://localhost/demo | Hızlı login |
| 3 | http://localhost:3000 | Grafana (admin/admin) |
| 4 | http://localhost:9411 | Zipkin |
| 5 | https://github.com/eaaslan/springboot-ecommerce | GitHub |

**IDE'yi (IntelliJ veya VS Code) de açık tut**, ilk 2 dakikada proje
yapısını gösterirken kullanacağız. Project view'da kök klasör görünür
olsun.

---

## Kayıt — dakika dakika

> Sessiz ekran kaydı çek + voice-over sonradan. Bu metni oku.

---

### 0:00 – 0:20 · Açılış

**Söyle:**

> "Merhaba, bu projede n11 ya da Trendyol gibi çok satıcılı bir e-ticaret
> sitesi geliştirdim. Önce projenin yapısını göstereyim, sonra siteyi canlı
> kullanırken anlatacağım."

**Ekran:** README'nin üst kısmı veya doğrudan IDE.

---

### 0:20 – 1:30 · Proje yapısı

**Tıkla:** IDE'de proje köküne git.

**Söyle:**

> "Proje iki ayrı repository'den oluşuyor — backend Spring Boot, frontend
> React. Şimdi backend'in içine bakalım, kabaca neresinde ne var?"

**Tıkla:** `services/` klasörünü aç (tek tek alt klasörlere girme, sadece üst seviyeyi göster).

**Söyle:**

> "Burada her servis ayrı bir iş yapıyor — kullanıcı, ürün, sepet, sipariş,
> ödeme, satıcı, bildirim gibi.
>
> Birkaçı özel: **recommendation** öneri motoru, hem siteye hem MCP
> üzerinden yapay zekaya servis veriyor. **catalog-stream** WebFlux ile
> reactive bir okuma katmanı.
>
> Her servisin kendi unit ve integration testleri var — toplam 80+ test,
> CI pipeline'ında otomatik koşuyor. Postgres ihtiyacı olan testler
> Testcontainers ile gerçek DB üzerinde çalışıyor."

**Tıkla:** `infrastructure/` klasörü.

**Söyle:**

> "API gateway, Eureka discovery, config server. Servislerin önündeki
> altyapı katmanı."

**Tıkla:** `shared/common/`

**Söyle:**

> "Ortak DTO'lar, error model, correlation filter. Tekrar etmesin diye
> ortak kütüphane."

**Tıkla:** Kök klasördeki 3 compose dosyasını göster.

**Söyle:**

> "Üç compose dosyası: `docker-compose.yml` source'tan build edip her
> şeyi ayağa kaldırır, `prod.yml` GHCR'dan image çeker, `infra.yml`
> sadece bağımlılıkları açar IDE'de geliştirirken kullanıyorum."

**Tıkla:** `aws/` ve `docs/` klasörlerini hızlıca göster.

**Söyle:**

> "`aws/` Beanstalk deploy paketi. `docs/` dokümantasyon ve API
> koleksiyonu. Şimdi canlı akışa geçelim."

---

### 1:30 – 3:00 · Müşteri akışı

**Tıkla:** Tarayıcı → `/demo` sekmesi → "Buyer #1" → "Login"

**Söyle:**

> "Demo için bir login switcher hazırladım — videoda her seferinde şifre
> yazmak istemediğim için. Buyer1 ile giriyorum."

**Ekran:** Ana sayfa.

**Söyle:**

> "Ana sayfa. n11/Trendyol mantığında çoklu satıcı buy-box'ı var —
> en uygun teklif kart üzerinde."

**Söyle (Recommended for you bandının altındayken kısa):**

> "Üstte 'Recommended for you' — recommendation servisinden, basit
> içerik tabanlı benzerlik algoritması. Aynı servis daha sonra MCP
> üzerinden Claude'a da açılacak."

**Tıkla:** Listing'li bir ürünü aç.

**Söyle:**

> "Ürün detayı. Sağda fiyat ve sepete ekleme. Aşağıda 'diğer satıcılar'
> tablosu — aynı ürünü daha pahalıya satan satıcılar."

**Tıkla:** "Add to Cart" → Cart → "Checkout" → "Place order"

**Söyle (yavaş):**

> "Bu tıklamayla saga başlıyor: cart fetch, Iyzico'da kart authorization,
> stok rezervasyonu, sub-order split, outbox event, Kafka publish.
> Adımların herhangi biri patlarsa compensation devreye giriyor — refund
> + reservation release."

**Tıkla:** Order detail'in "Per-seller breakdown" kısmı.

**Söyle:**

> "Burada görüyorsunuz, satıcı başına ayrı satır var. Her satıcının
> aldığı tutar, bizim aldığımız komisyon, satıcıya ödenecek net miktar
> ayrı ayrı tutuluyor."

---

### 3:00 – 4:00 · Satıcı olma akışı

**Tıkla:** Header → "Become a Seller"

**Söyle:**

> "Şimdi 'satıcı olmak istiyorum' butonuna basalım. Demo için hazır
> şablonlar koydum, tek tıkla form dolacak."

**Tıkla:** "Anadolu Ticaret — Use this draft" → "Submit"

**Söyle:**

> "Başvuru gönderildi, PENDING durumda. Şimdi admin tarafına geçiyorum."

**Tıkla:** `/demo` → Alice → header → "Sellers" → PENDING filter → "Approve"

**Söyle:**

> "Onayladım. Saniyeler içinde aktif satıcı oldu. Artık ürün listeleyebilir,
> sipariş alabilir."

---

### 4:00 – 4:45 · Para akışı

**Tıkla:** Header → "Payouts"

**Söyle:**

> "Payout ledger — haftalık batch. Admin period seçer, sistem o aralıktaki
> PENDING sub-orderları satıcı bazında aggregate eder. UNIQUE
> `(seller_id, period_start, period_end)` olduğu için run idempotent —
> aynı period'u tekrar bastırınca yeni satır oluşmuyor."

---

### 4:45 – 5:45 · Bir siparişin yolculuğu (★)

**Tıkla:** Zipkin sekmesi → service: `api-gateway` → Run query → en son trace

**Söyle:**

> "Az önce attığım siparişin Zipkin trace'i. Tek istek 8 spans:
> gateway, order-service, cart, inventory, payment, seller (commission
> lookup), outbox publisher, Kafka producer. OpenTelemetry +
> Micrometer Tracing köprüsü, gateway'de correlation ID propagate
> ediyor."

---

### 5:45 – 6:30 · İzleme paneli

**Tıkla:** Grafana → "Microservices Overview"

**Söyle:**

> "Grafana — RED metrikleri (rate, errors, duration) her servis için.
> Business counter'lar da var: orders.placed, orders.cancelled (reason
> tag'iyle), coupon.redeemed. Prometheus 13 servisten 15s'de scrape
> ediyor."

---

### 6:30 – 7:15 · Yapay zeka entegrasyonu (★)

**Tıkla:** Claude Desktop / Claude Code'a geç

**Söyle:**

> "Recommendation servisi aynı zamanda MCP server expose ediyor —
> Spring AI ile, SSE üzerinden. searchProducts, similarProducts,
> recommendForUser tool'ları."

**Yaz:**
```
What headphones do you have under 2000 TRY?
```

**Söyle:**

> "Claude tool'u çağırdı, gerçek katalogtan dönüş geldi. AI agent'larının
> backend'lere bağlanma standardı — REST'i MCP'ye bind ettim."

---

### 7:15 – 8:00 · Nasıl çalıştırılır

**Tıkla:** Terminal → README

**Söyle:**

> "Sıfırdan klon → `docker compose up --build` → 21 container.
> 13 backend, frontend, Postgres, Redis, RabbitMQ, Kafka, Prometheus,
> Grafana, Zipkin. Host'ta JDK/Node yok, hepsi Buildx + Maven container
> içinde."

**Tıkla:** GitHub → Actions

**Söyle:**

> "GitHub Actions: her main push Jib ile multi-arch image basıyor, GHCR'a
> atıyor. Slack webhook'u success/failure'da notification gönderiyor.
> AWS Beanstalk için RDS + ElastiCache'li ayrı deploy paketi `aws/`
> klasöründe."

---

### 8:00 – 9:00 · Geçmiş

**Tıkla:** README → Roadmap

**Söyle:**

> "13 phase'de büyüdü. Phase 0-12 foundation: auth, catalog, cart, saga,
> RabbitMQ, Kafka outbox, observability, Spring AI. Phase 13'te
> marketplace V1-V4: seller domain, listing-aware cart, sub-orders +
> commission, reviews + payouts + returns. Her milestone git tag,
> reversible branch."

---

### 9:00 – 10:00 · Kapanış (geniş bırak)

**Tıkla:** README üstüne dön

**Söyle:**

> "80+ test, CI yeşil. README'de bring-up rehberi, mimari diyagramı,
> troubleshooting var. GitHub linki açıklamada. Sorulara açığım."

---

## Sorun çıkarsa

| Sorun | Çözüm |
|---|---|
| Site açılmıyor | `docker compose ps` — hangi servis kırmızı? |
| Görseller yok | `node scripts/seed/backfill-images.js` |
| Login olmuyor | seed atılmamış: `./docs/demo/prep-demo.sh` |
| Order verince 503 | 30 saniye bekle (servis kayıtları güncellensin) |

## Voice-over taktikleri

- **Yavaş konuş.** "Hızlı ve düzgün" yerine "yavaş ama akıcı".
- **"Şu an buradayım, şuraya geçiyorum"** — yönlendirme bilgisi.
- **"Şimdi şuna dikkat edin..."** — wow moment'ten önce vurgu.
- "Mesela", "yani", "bakın" gibi konuşma dilinden kelimeler doğal hissettirir.
- **Sayı ezberleme.** "8 servis" yerine "yaklaşık on tane" daha akıcı.
- Atlamak gerekirse "şimdi onu geçiyorum" demek "uhh" demekten iyi.

## Kayıttan SONRA

```bash
docker compose down -v
```

Başarılar 🎬
