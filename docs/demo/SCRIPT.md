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

> "Her servis ayrı bir işten sorumlu — kullanıcı, ürün, sepet, sipariş,
> ödeme, satıcı, bildirim gibi. Birbirinden bağımsız çalışıyorlar, her
> birinin kendi veritabanı var.
>
> Birkaçı biraz farklı: **recommendation** öneri motoru — hem siteye
> 'sana özel' önerileri o veriyor, hem de MCP üzerinden Claude gibi
> yapay zeka asistanlarına aynı servis bağlanıyor. **catalog-stream** ise
> WebFlux ile yazılmış bir okuma servisi, yüksek trafikte performans
> kaybetmiyor.
>
> Test tarafında her servisin kendi unit ve integration testleri var —
> toplamda 80 küsür test, CI'da otomatik koşuyor. Veritabanı isteyen
> testler için Testcontainers kullanıyorum, yani test sırasında gerçek
> Postgres ayağa kalkıyor, mock'la kandırmıyor."

**Tıkla:** `infrastructure/` klasörü.

**Söyle:**

> "Burada API gateway, Eureka discovery ve config server var.
> Servislerin önünde duran altyapı katmanı — gateway dışarıdan gelen
> isteklerin tek giriş kapısı, JWT kontrolü orada."

**Tıkla:** `shared/common/`

**Söyle:**

> "Tüm servislerin paylaştığı küçük bir kütüphane — ortak DTO'lar,
> hata mesajı formatı, log filter'ları. Her serviste tekrar yazmamak
> için."

**Tıkla:** Kök klasördeki 3 compose dosyasını göster.

**Söyle:**

> "Üç tane compose dosyası var:
> **`docker-compose.yml`** ana dosya, klonlayıp tek komutla her şeyi
> kaynaktan build edip kaldırıyor. **`prod.yml`** sunucuda kullanılıyor,
> bu sefer build etmiyor, GHCR'dan hazır image çekiyor. **`infra.yml`**
> ben IDE'de geliştirirken — sadece veritabanı, Redis, Kafka gibi
> bağımlılıkları açıyor, servisleri kendim çalıştırıyorum."

**Tıkla:** `aws/` ve `docs/` klasörlerini hızlıca göster.

**Söyle:**

> "`aws/` AWS Beanstalk için ayrı deploy paketi — RDS ve ElastiCache'e
> bağlı bir compose dosyası, adım adım rehber. `docs/` içinde
> dokümantasyon, demo scripti, API koleksiyonu. Şimdi canlı kullanmaya
> geçelim."

---

### 1:30 – 3:00 · Müşteri akışı

**Tıkla:** Tarayıcı → `/demo` sekmesi → "Buyer #1" → "Login"

**Söyle:**

> "Demo için bir login switcher hazırladım — videoda her seferinde şifre
> yazmak istemediğim için. Buyer1 ile giriyorum."

**Ekran:** Ana sayfa.

**Söyle:**

> "Ana sayfa. n11/Trendyol mantığında çoklu satıcı yapısı var —
> aynı ürünü farklı satıcılar farklı fiyata satabiliyor, sistem en
> uygun teklifi seçip kart üzerinde gösteriyor. Buy-box mantığı."

**Söyle (Recommended for you bandının altındayken kısa):**

> "Üstte 'Recommended for you' bandı var — bu öneriler recommendation
> servisinden geliyor, kullanıcının baktığı kategoriye göre içerik
> tabanlı benzerlik hesaplıyor. Az sonra göstereceğim, aynı servis MCP
> üzerinden Claude'a da bağlanıyor."

**Tıkla:** Listing'li bir ürünü aç.

**Söyle:**

> "Ürün detayı. Sağda fiyat ve sepete ekleme. Aşağıda 'diğer satıcılar'
> tablosu — aynı ürünü daha pahalıya satan satıcılar."

**Tıkla:** "Add to Cart" → Cart → "Checkout" → "Place order"

**Söyle (yavaş):**

> "Bu tıklamayla arkada saga başlıyor. Sırasıyla: sepet bilgisi
> çekiliyor, Iyzico'ya kart gönderilip authorize ediliyor, stok
> ayrılıyor, sipariş satıcı başına ayrı sub-order'lara bölünüyor —
> her satıcının komisyonu ve net ödenecek tutarı ayrı hesaplanıyor.
> En sonunda Kafka'ya bir event atılıyor, bunu bildirim servisi
> tüketiyor.
>
> Önemli kısım: bir adımda hata olursa compensation devreye giriyor.
> Mesela ödeme reddedilirse, ayrılan stok serbest bırakılıyor,
> sipariş iptal ediliyor — yarım kalmış bir sipariş bırakmıyoruz."

**Tıkla:** Order detail'in "Per-seller breakdown" kısmı.

**Söyle:**

> "Burada görüyorsunuz, satıcı başına ayrı satır var. Her satıcının
> aldığı tutar, bizim aldığımız komisyon, satıcıya ödenecek net miktar
> ayrı ayrı tutuluyor."

---

> **NOT (kayıt sırasında)**: Bu sipariş hangi satıcıdan geldiyse, o
> satıcıya geçince incoming orders'da görüneceğiz — script bunu
> aşağıda açıklıyor. prep-demo.sh her seed satıcısına bir test sipariş
> düşürdüğü için demo paneli'nden hangi satıcıya tıklasanız
> /seller/orders'da en az 1 sipariş gözükecek.

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

> "Onayladım. Saniyeler içinde aktif satıcı oldu."

**Tıkla:** `/demo` → seed satıcılardan birine tıkla (örn TechMart) → `/seller/dashboard` → "Incoming orders"

**Söyle:**

> "Şimdi de seed satıcılarımızdan birine geçelim — TechMart Elektronik'e.
> Burası satıcının kendi paneli: dashboard'da iş özeti, listings'te kendi
> ürünleri, ve incoming orders'ta kendisine gelen siparişler görünüyor.
> Az önce buyer1'in verdiği sipariş bu listede — sub-order id, müşteri,
> komisyon ve net ödenecek tutar."

---

### 4:00 – 4:45 · Para akışı

**Tıkla:** Header → "Payouts"

**Söyle:**

> "Burası ödeme defteri — payout ledger. Trendyol mantığı: müşteriden
> para alındı ama satıcıya hemen ödenmez, haftalık batch'lerle toplu
> ödeniyor.
>
> Admin tarih aralığı seçer, 'Run payout' der, sistem o aralıktaki
> PENDING sub-orderları satıcı bazında toplayıp tek satır çıkarıyor —
> 'TechMart'a bu hafta 4500 TL ödenecek' gibi.
>
> Idempotent yazdım: aynı tarihi tekrar bastırırsam yeni satır
> oluşmuyor, çünkü `(seller_id, period_start, period_end)` üzerinde
> unique constraint var. Çift ödeme imkansız."

---

### 4:45 – 5:45 · Bir siparişin yolculuğu (★)

**Tıkla:** Zipkin sekmesi → service: `api-gateway` → Run query → en son trace

**Söyle:**

> "Az önce verdiğim siparişin Zipkin trace'ine bakalım. Tek bir checkout
> isteği 8 farklı servisi geziyor — gateway, order-service, sepet, stok,
> ödeme, satıcı (komisyon için), outbox, Kafka. Hepsinin timing'i burada
> görünüyor.
>
> Altyapı tarafında OpenTelemetry + Micrometer Tracing var, gateway
> her isteğe correlation ID ekliyor, log'larda da aynı ID akıyor —
> bir hata olursa istemden log'a kadar takip edilebiliyor."

---

### 5:45 – 6:30 · İzleme paneli

**Tıkla:** Grafana → "Microservices Overview"

**Söyle:**

> "Grafana paneli. Her servis için RED metrikleri — yani saniyedeki
> istek sayısı, hata oranı, gecikme. Prometheus 13 servisten 15
> saniyede bir metrik çekip burada gösteriyor.
>
> İş tarafı için ekstra sayaçlar da var: kaç sipariş verildi, kaç
> tanesi iptal oldu ve hangi sebeple, kaç kupon kullanıldı. Business
> tarafının görmek isteyeceği şeyler."

---

### 6:30 – 7:15 · Yapay zeka entegrasyonu (★)

**Tıkla:** Claude Desktop / Claude Code'a geç

**Söyle:**

> "Bonus özellik — biraz önce bahsettiğim recommendation servisi aynı
> zamanda MCP sunucusu. Spring AI ile yazıldı, SSE üzerinden çalışıyor.
> Üç tool açıyor: ürün arama, benzer ürünler, kullanıcıya öneri."

**Yaz:**
```
What headphones do you have under 2000 TRY?
```

**Söyle:**

> "Bakın, 'hangi kulaklık var 2000 liranın altında' diye sordum, Claude
> doğrudan benim katalogtan çekti, listeledi. Gerçek bir tool çağrısı.
>
> AI agent'ların backend'lere bağlanma standardı MCP — REST API'lerimi
> tool olarak expose ettim, Claude/Cursor/Claude Code aynı şekilde
> kullanabiliyor."

---

### 7:15 – 8:00 · Nasıl çalıştırılır

**Tıkla:** Terminal → README

**Söyle:**

> "Projenin en güzel yanı: tek komutla çalışıyor. Yeni biri repo'yu
> klonlasın, `docker compose up --build` desin, 10 dakika içinde 21
> container ayağa kalkıyor. 13 backend servisi, frontend, Postgres,
> Redis, RabbitMQ, Kafka ve izleme araçları. Host'ta Java veya Node
> bile gerekmez — Maven build container içinde, frontend Buildx ile
> başka repo'dan otomatik geliyor."

**Tıkla:** GitHub → Actions

**Söyle:**

> "Her main branch'a push'ladığımda GitHub Actions otomatik build
> çalıştırıyor. Image'ları Jib ile multi-arch şekilde — yani hem ARM
> hem x86 için — üretip GHCR'a yüklüyor. Slack webhook'u tanımlandıysa
> başarılı veya başarısız her deploy'da bildirim gidiyor.
>
> AWS tarafında da yer var: `aws/` klasöründe Beanstalk için ayrı bir
> deploy paketi hazırladım — RDS ve ElastiCache'e bağlı, adım adım
> rehberle birlikte."

---

### 8:00 – 9:00 · Geçmiş

**Tıkla:** README → Roadmap

**Söyle:**

> "Proje 13 aşamada büyüdü. İlk 12 aşama altyapı: auth, ürün kataloğu,
> sepet, saga, RabbitMQ + Kafka outbox, observability, Spring AI.
>
> 13. aşamada marketplace eklendi, dört adımda: önce satıcı kayıtları
> ve listing'ler, sonra sepetin satıcıya bağlanması, sonra sub-order
> ayrıştırması ve komisyon hesabı, en son yorumlar + payout + iadeler.
>
> Her aşama için git'te tag bıraktım, geri-dönülebilir bir
> branch'te tuttum — beğenmezsem tek komutla geri alırım."

---

### 9:00 – 10:00 · Kapanış (geniş bırak)

**Tıkla:** README üstüne dön

**Söyle:**

> "Son olarak: 80'in üzerinde test var, CI'da hepsi otomatik koşuyor.
> README'de baştan sona kurulum rehberi, mimari diyagramı, sık
> karşılaşılan sorunların çözümleri var. GitHub linki açıklamada.
> Sorulara açığım, dinlediğiniz için teşekkürler."

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
