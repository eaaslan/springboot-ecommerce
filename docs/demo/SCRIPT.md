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

> "Burası ana iş klasörü. Her bir alt klasör tek bir işten sorumlu ayrı
> bir program — kullanıcı yönetimi, ürün, sepet, sipariş, ödeme, satıcı,
> bildirim gibi. Bunlara microservice deniyor, hepsi birbirinden
> bağımsız çalışıyor.
>
> Birkaç servis biraz farklı: **recommendation** servisi öneri motoru,
> hem siteye hem de yapay zekaya bağlanıyor. **catalog-stream** ise hızlı
> okuma için tasarlanmış alternatif bir servis — Spring'in reactive (yani
> non-blocking) tarafıyla yazıldı, çok yüksek trafik altında bile
> performans kaybetmiyor."

**Tıkla:** `infrastructure/` klasörü.

**Söyle:**

> "Burada 3 altyapı servisi var. Bunlar iş yapmıyor, servislerin
> birbirini bulmasını ve trafiği yönetmesini sağlıyorlar."

**Tıkla:** `shared/common/`

**Söyle:**

> "Tüm servisler ortak küçük bir kütüphane kullanıyor — hata mesajı
> formatı, log filter'ları gibi tekrar etmesin diye buraya topladım."

**Tıkla:** Kök klasördeki 3 compose dosyasını göster.

**Söyle:**

> "Üç tane compose dosyası var:
>
> - **`docker-compose.yml`** ana dosya. Birisi repo'yu klonlar, tek komut der,
>   her şey çalışır.
> - **`docker-compose.prod.yml`** sunucuya deploy ederken — GitHub'tan hazır
>   image'ları çekiyor, daha hızlı.
> - **`docker-compose.infra.yml`** ben kodu IDE'de yazarken kullandığım — sadece
>   veritabanı gibi altyapıyı açıyor, servisleri kendim çalıştırıyorum."

**Tıkla:** `aws/` ve `docs/` klasörlerini hızlıca göster.

**Söyle:**

> "`aws` klasöründe AWS Beanstalk için ayrı deploy paketi var. `docs`
> içinde dokümantasyon, kurulum rehberi, demo scripti ve API koleksiyonu.
>
> Kısacası: birkaç bağımsız servis, ortak altyapı, farklı senaryolar için
> compose dosyaları, deploy paketleri. Şimdi canlı kullanmaya geçelim."

---

### 1:30 – 3:00 · Müşteri akışı

**Tıkla:** Tarayıcı → `/demo` sekmesi → "Buyer #1" → "Login"

**Söyle:**

> "Demo için bir login switcher hazırladım — videoda her seferinde şifre
> yazmak istemediğim için. Buyer1 ile giriyorum."

**Ekran:** Ana sayfa.

**Söyle:**

> "Ana sayfa. Ürün kartları, fiyat, stok, satıcı bilgisi. Birden fazla
> satıcı aynı ürünü farklı fiyatla satabiliyor. Site otomatik olarak en
> uygun teklifi seçip ön plana koyuyor — buna 'buy-box' deniyor, n11 ve
> Trendyol da aynı sistemi kullanıyor."

**Söyle (Recommended for you bandının altındayken kısa):**

> "Üstte 'Recommended for you' bandı var — bu öneriler ayrı bir servisten
> geliyor. Kullanıcının daha önce baktığı ürün kategorisine göre benzer
> ürünleri sıralıyor, basit bir benzerlik algoritmasıyla. Sonra göreceğiz,
> aynı servis yapay zekaya da açılıyor."

**Tıkla:** Listing'li bir ürünü aç.

**Söyle:**

> "Ürün detayı. Sağda fiyat ve sepete ekleme. Aşağıda 'diğer satıcılar'
> tablosu — aynı ürünü daha pahalıya satan satıcılar."

**Tıkla:** "Add to Cart" → Cart → "Checkout" → "Place order"

**Söyle (yavaş):**

> "Bu butona basınca arkada çok şey oluyor. Önce kart kontrol ediliyor —
> Iyzico'nun gerçek API'sine bağladım. Aynı anda stok ayrılıyor, sipariş
> veritabanına yazılıyor, satıcıya ödenecek tutar hesaplanıyor.
>
> En son Kafka üzerinden bir bildirim mesajı gidiyor. Bu mesajı bildirim
> servisi yakalıyor.
>
> Hepsi tek tıkla. Bir hata olursa sistem geri sarıyor — stoku iade
> ediyor, sipariş iptal oluyor."

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

> "Burası ödeme listesi. Trendyol'un mantığı: müşteriden para alındı ama
> satıcıya hemen verilmez — haftada bir toplu ödeme yapılır.
>
> Admin tarih aralığı seçer, 'Run payout' der, sistem tüm siparişleri
> satıcı bazında toplayıp tek satırda gösterir. Burada 'Bu hafta TechMart'a
> 4500 TL ödeyeceğiz' yazıyor, içinde komisyon düşülmüş halde.
>
> Bir özellik daha: aynı tarihi tekrar bastırmak istesem yeni satır
> oluşmuyor. Çift ödeme imkânsız."

---

### 4:45 – 5:45 · Bir siparişin yolculuğu (★)

**Tıkla:** Zipkin sekmesi → service: `api-gateway` → Run query → en son trace

**Söyle:**

> "Şimdi 'az önce verdiğim sipariş arkada kim ne yaptı, ne kadar sürdü'
> sorusunu cevaplayan ekrana gelelim.
>
> Bakın, tek bir 'sipariş ver' butonu 8 farklı servisi geziyor:
>
> Önce gateway giriyor — yani trafik kapısı. Sonra sipariş servisi
> devreye giriyor. Sepet servisinden cart bilgisini çekiyor. Stok
> servisinden ürünü ayırıyor. Ödeme servisini çağırıyor. Satıcı
> servisinden komisyon oranını öğreniyor. Sonra Kafka'ya bildirim
> atıyor.
>
> Hepsi sıralı, mili saniye düzeyinde ölçülüyor. Bir yerde gecikme
> olursa burada hemen görünüyor."

---

### 5:45 – 6:30 · İzleme paneli

**Tıkla:** Grafana → "Microservices Overview"

**Söyle:**

> "Burası Grafana — production'da kullanılan izleme aracı.
>
> Sol üstte canlı istek sayısı: dakikada kaç istek geliyor.
> Yanında hata oranı.
> Altta gecikme grafikleri.
>
> İş tarafı için de ekstra metrikler var — kaç sipariş verildi, kaç
> tanesi iptal oldu. Business tarafının isteyeceği şeyler."

---

### 6:30 – 7:15 · Yapay zeka entegrasyonu (★)

**Tıkla:** Claude Desktop / Claude Code'a geç

**Söyle:**

> "Bir bonus — biraz önce bahsettiğim öneri servisini yapay zekaya da
> açtım. Yani aynı servis hem siteye 'sana özel öneriler' hem de Claude
> Desktop, Cursor gibi yapay zeka asistanlarına ürün arama hizmeti veriyor."

**Yaz:**
```
What headphones do you have under 2000 TRY?
```

**Söyle:**

> "Bakın, 'hangi kulaklık var 2000 liranın altında' diye sordum, Claude
> direkt benim siteme bağlandı, ürünleri çekti, listeledi.
>
> Yarın yapay zekanın e-ticarete nasıl bağlanacağı konusunda öncü bir
> özellik bu."

---

### 7:15 – 8:00 · Nasıl çalıştırılır

**Tıkla:** Terminal → README

**Söyle:**

> "Projenin en güzel yanı: tek komutla çalışıyor. Yeni biri repo'yu
> klonlasın, `docker compose up --build` desin, 10 dakika içinde 21
> container ayağa kalkıyor — 13 backend, frontend, veritabanı, cache,
> mesaj kuyrukları, izleme. Bilgisayara hiçbir şey kurmasına gerek yok."

**Tıkla:** GitHub → Actions

**Söyle:**

> "Her main branch push'unda otomatik build çalışıyor — image'lar GitHub'ın
> kayıtçısına atılıyor. AWS Beanstalk için ayrı bir deploy paketi de var."

---

### 8:00 – 9:00 · Geçmiş

**Tıkla:** README → Roadmap

**Söyle:**

> "Proje 13 aşamada büyüdü. İlk 12 aşamada altyapı hazırlandı: kullanıcı
> girişi, ürün listesi, sepet, sipariş, ödeme, mesajlaşma, izleme, yapay
> zeka. 13. aşamada marketplace eklendi — dört adımda: önce satıcı
> kayıtları, sonra sepetin satıcıya bağlanması, sonra para akışının
> ayrılması, en son yorumlar ve iadeler.
>
> Her aşama git'te ayrı tag — istediğim noktaya geri dönebilirim."

---

### 9:00 – 10:00 · Kapanış (geniş bırak)

**Tıkla:** README üstüne dön

**Söyle:**

> "Son olarak: 80'in üzerinde test var, hepsi otomatik koşuyor. README'de
> tüm kurulum adımları yazıyor. GitHub linki açıklamada.
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
