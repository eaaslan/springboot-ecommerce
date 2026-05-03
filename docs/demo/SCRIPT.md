# 10 dakikalık demo videosu — basit anlatım

Mülakat için sade, anlaşılır bir tanıtım. Konuşma dili, kısa cümleler.

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

---

## Kayıt — dakika dakika

> Sessiz ekran kaydı çek + voice-over sonradan. Bu metni oku.

---

### 0:00 – 0:30 · Açılış

**Söyle:**

> "Merhaba, bu projede bir e-ticaret sitesi geliştirdim. n11 veya Trendyol gibi, bir sitede hem kendi ürünlerini koyabilen birden fazla satıcı var, hem de müşteriler ürün alıp yorum yapabiliyor.
>
> Backend tarafında 13 ayrı küçük servis var — her biri kendi işine bakıyor. Frontend React ile yazıldı. Tek komutla çalıştırılabiliyor.
>
> Şimdi adım adım göstereyim."

**Ekran:** README'nin üst kısmı, mimari diyagram.

---

### 0:30 – 2:30 · Müşteri akışı

**Söyle:**

> "İlk olarak müşteri tarafına bakalım. Bir login switcher hazırladım — videolarda her seferinde şifre yazmak zorunda kalmayayım diye."

**Tıkla:** `/demo` sekmesi → "Buyer #1" → "Login & go to /"

**Ekran:** Ana sayfa, ürün grid'i.

**Söyle:**

> "İşte ana sayfa. n11 görünümlü kartlar, ürün başına satıcı bilgisi, fiyat ve stok. Her ürünün altında 'Sold by ...' yazıyor — bu ürünü hangi satıcının verdiğini gösteriyor.
>
> Birden fazla satıcı aynı ürünü farklı fiyatla satabiliyor. Site otomatik olarak en uygun teklifi seçip ön plana koyuyor — buna 'buy-box' deniyor, n11 ve Trendyol da aynı sistemi kullanıyor."

**Tıkla:** Listing'li bir ürünü aç (ikinci sayfadan biri).

**Ekran:** Ürün detay sayfası.

**Söyle:**

> "Ürün detay sayfası. Sağda fiyat ve sepete ekleme butonu. Aşağıda 'diğer satıcılar' tablosu — aynı ürünü daha pahalıya satan satıcılar burada."

**Tıkla:** Quantity 1 → "Add to Cart" → Cart sekmesi

**Ekran:** Sepet.

**Söyle:**

> "Sepete eklendi. Ürünün altında satıcı adı duruyor — sepetin hangi satıcıyla bağlantılı olduğu hatırlanıyor."

**Tıkla:** "Checkout" → kart bilgileri zaten dolu → "Place order"

**Ekran:** Sipariş onay sayfası → order detail.

**Söyle (yavaş, önemli):**

> "Bu butona basınca arkada çok şey oluyor. Önce kart kontrol ediliyor — Iyzico'nun gerçek API'sine bağladım, sandbox'ta gerçek ödeme isteği gidiyor.
>
> Aynı anda stok ayrılıyor, sipariş veritabanına yazılıyor, satıcıya ödenecek tutar hesaplanıyor — yani komisyonu düşüp net miktarı belirliyor.
>
> En sonunda da Kafka üzerinden bir bildirim mesajı gidiyor, 'sipariş onaylandı' diye. Bu mesajı bildirim servisi yakalıyor.
>
> Hepsi tek bir tıklamayla. Eğer ortada bir hata olursa — mesela kart reddedilirse — sistem geri sarıyor, stoku iade ediyor, sipariş iptal oluyor."

**Tıkla:** Order detail'in "Per-seller breakdown" kısmını göster.

**Söyle:**

> "Burada görüyorsunuz, satıcı başına ayrı satır var. Her satıcının aldığı tutar, bizim aldığımız komisyon, satıcıya ödenecek net miktar — hepsi ayrı ayrı tutuluyor."

---

### 2:30 – 4:00 · Satıcı olma akışı

**Söyle:**

> "Şimdi 'satıcı olmak istiyorum' butonuna basalım. Demo için bu kısımda hazır şablonlar koydum, tek tıkla form dolacak."

**Tıkla:** Header'dan "Become a Seller"

**Ekran:** Başvuru formu, üstte 3 hazır şablon.

**Tıkla:** "Anadolu Ticaret — Use this draft" → form doldu → "Submit application"

**Söyle:**

> "Başvuru gönderildi. Şu an 'PENDING' durumda — admin onayı bekliyor."

**Tıkla:** `/demo` sekmesi → Alice (admin) → /admin/products'a düşer

**Söyle:**

> "Admin tarafına geçtim. Sol üstte Products, Coupons, Sellers, Payouts var. Sellers'a tıklıyorum."

**Tıkla:** Header → "Sellers" → status filter PENDING

**Ekran:** PENDING başvuru görünüyor.

**Tıkla:** "Approve" butonu

**Söyle:**

> "Onayladım. Saniyeler içinde satıcı aktif oldu. Artık ürün listeleyebilir, sipariş alabilir."

---

### 4:00 – 5:00 · Para akışı (payout)

**Tıkla:** Header → "Payouts"

**Ekran:** Payouts tablosu (prep-demo.sh sayesinde dolu).

**Söyle:**

> "Burası ödeme listesi. Trendyol'un mantığı: müşteriden para alındı ama satıcıya hemen verilmez — haftada bir veya 15 günde bir toplu ödeme yapılır.
>
> Ben de aynısını yaptım. Admin tarih aralığı seçer, 'Run payout' der, sistem o aralıktaki tüm siparişleri satıcı bazında toplayıp tek satırda gösterir.
>
> Mesela burada 'Bu hafta TechMart'a 4.500 TL ödeyeceğiz' yazıyor, içinde 8% komisyon düşülmüş hâlde.
>
> Bir özelliği daha var: aynı tarihi tekrar bastırmak istesem yeni satır oluşmuyor — kontrol var, çift ödeme olamıyor."

---

### 5:00 – 6:00 · Bir siparişin yolculuğu (★)

**Tıkla:** Zipkin sekmesi (http://localhost:9411)

**Söyle:**

> "Şimdi 'az önce verdiğim sipariş kim ne yaptı, ne kadar sürdü' sorusunu cevaplayan ekrana gelelim. Buna 'distributed tracing' deniyor."

**Tıkla:** Service name: `api-gateway` → Run query → en son trace'i seç

**Ekran:** Trace timeline.

**Söyle:**

> "Bakın, tek bir 'sipariş ver' butonu 8 farklı servisi geziyor:
>
> Önce gateway giriyor — yani trafik kapısı. Sonra sipariş servisi devreye giriyor.
> Sepet servisinden cart bilgisini çekiyor.
> Stok servisinden ürünü ayırıyor.
> Ödeme servisini çağırıyor — Iyzico'ya istek gidiyor.
> Satıcı servisinden komisyon oranını öğreniyor.
> En son Kafka'ya bildirim mesajı atıyor.
>
> Hepsi sıralı, hepsi mili saniye düzeyinde ölçülüyor. Bir yerde gecikme olursa burada hemen görünüyor."

---

### 6:00 – 7:00 · İzleme paneli

**Tıkla:** Grafana sekmesi → "Microservices Overview" dashboard

**Söyle:**

> "Burası Grafana — production'da kullanılan izleme aracı.
>
> Sol üstte canlı istek sayısı: dakikada kaç istek geliyor.
> Yanında hata oranı: ne kadar 4xx, 5xx dönüyoruz.
> Altta gecikme grafikleri: en yavaş istek hangi servisteydi.
>
> Bunların hepsi Prometheus'tan geliyor. Her servis kendi metriklerini açıyor, Prometheus 15 saniyede bir çekiyor, Grafana çiziyor.
>
> Ek olarak iş metrikleri de var — kaç sipariş verildi, kaç tanesi iptal oldu, hangi sebeple iptal oldu. Bunları da business tarafı isteyebilir."

---

### 7:00 – 7:45 · Yapay zeka entegrasyonu (★)

**Tıkla:** Claude Desktop / Claude Code'a geç

**Söyle:**

> "Bir bonus daha ekledim — projeye yapay zeka erişimi.
>
> Claude Desktop, Cursor gibi yapay zeka asistanları artık doğrudan benim ürünlerimi sorgulayabilir. 'MCP server' adında yeni bir standart kullandım."

**Yaz:** (Claude'a)
```
What headphones do you have under 2000 TRY?
```

**Ekran:** Claude `searchProducts` tool'unu çağırır, ürünleri listeler.

**Söyle:**

> "Bakın, ben 'hangi kulaklık var 2000 liranın altında' diye sordum, Claude direkt benim siteme bağlandı, ürünleri çekti, listeledi.
>
> Yarın yapay zekanın e-ticaret platformlarına nasıl bağlanacağı konusunda öncü bir özellik bu. Şu an sadece arama, benzer ürün ve öneri var ama genişletilebilir."

---

### 7:45 – 8:45 · Nasıl çalıştırılır

**Tıkla:** Terminal aç, README'yi göster.

**Söyle:**

> "Projeyi en güzel yanı: tek komutla çalışıyor.
>
> Yeni biri repo'yu klonlasın, `docker compose up --build` desin, 10 dakika içinde 21 container ayağa kalkıyor:
> - 13 backend servisi
> - Frontend
> - Veritabanı, cache, mesaj kuyrukları, izleme araçları
>
> Bilgisayara hiçbir şey kurmasına gerek yok — Java da, Maven da, Node da yok. Hepsi Docker içinde."

**Tıkla:** GitHub → Actions sekmesi.

**Söyle:**

> "Ayrıca her main branch'a push'ladığımda otomatik build çalışıyor — GitHub'ın kendi sistemi (Actions). Image'lar otomatik üretilip GitHub'ın container kayıtçısına atılıyor. Sunucuya da tek komutla deploy edilebiliyor.
>
> AWS Elastic Beanstalk için ayrı bir deploy paketi de hazırladım — `aws/` klasöründe."

---

### 8:45 – 9:30 · Geçmiş

**Tıkla:** README → Roadmap tablosu.

**Söyle:**

> "Proje 13 ayrı aşamada büyüdü:
>
> 1. baştan 12'ye kadar: temel altyapı, kullanıcı girişi, ürün listesi, sepet, sipariş, ödeme, mesajlaşma, izleme, yapay zeka.
> 2. 13. aşamada marketplace'e dönüştü: dört adımda eklendi — önce satıcı kayıtları, sonra sepetin satıcıya bağlanması, sonra para akışının ayrılması, en son yorumlar ve iadeler.
>
> Her aşama için git'te ayrı tag bıraktım — istediğim noktaya geri dönebilirim."

---

### 9:30 – 10:00 · Kapanış

**Tıkla:** README üstüne dön.

**Söyle:**

> "Son olarak: 80'in üzerinde test var, hepsi otomatik koşuyor. README'de tüm kurulum adımları yazıyor. Github linki açıklamada.
>
> Soru ve geri bildiriminize açığım, teşekkür ederim."

---

## Sorun çıkarsa

| Sorun | Çözüm |
|---|---|
| Site açılmıyor | `docker compose ps` — hangi servis kırmızı? |
| Görseller yok | `node scripts/seed/backfill-images.js` |
| Login olmuyor | seed atılmamış: `./docs/demo/prep-demo.sh` |
| Order verince 503 | 30 saniye bekle (servis kayıtları güncellensin) |

## Voice-over taktikleri

- **Yavaş konuş.** "Hızlı ve düzgün" yerine "yavaş ama akıcı" çok daha iyi izleniyor.
- **"Şu an buradayım, şuraya geçiyorum"** — yönlendirme bilgisi izleyiciyi rahatlatır.
- **"Şimdi şuna dikkat edin..."** — wow moment'ten önce vurgu yap.
- "Mesela", "yani", "bakın" gibi konuşkan kelimeler doğal hissettirir, mülakatçı senaryo okuduğunu anlamaz.
- **Sayı ezberleme.** "8 servis" yerine "yaklaşık on tane" dersen daha akıcı.
- Bir şeyi atlamak gerekirse "şimdi onu geçiyorum" demek "uhh" demekten iyi.

## Kayıttan SONRA

```bash
docker compose down -v   # data temizliği
```

Başarılar 🎬
