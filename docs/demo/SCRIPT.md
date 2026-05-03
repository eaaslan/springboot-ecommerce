# 10 dakikalık demo videosu — basit anlatım

Mülakat için sade, anlaşılır bir tanıtım. Konuşma dili, kısa cümleler.
**İlk 2 dakika proje yapısı**, sonrası canlı demo.

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

### 0:20 – 2:00 · Proje yapısı turu (yeni)

**Tıkla:** IDE'de proje köküne git.

**Söyle:**

> "Proje iki ana parçadan oluşuyor. Backend Spring Boot ile yazıldı,
> frontend React. İkisi ayrı repository'de. Şimdi backend'in içine bakalım."

**Tıkla:** `services/` klasörünü aç.

**Söyle:**

> "Burası kalbi. 11 ayrı backend servisi var. Her biri tek bir işi
> yapıyor:
>
> - **user-service** kullanıcı kaydı, login, token üretiyor.
> - **product-service** ürün kataloğu, fiyat, stok.
> - **cart-service** müşterinin sepetini tutuyor — bu Redis'te, çünkü
>   sepet hızlı değişen bir şey, kalıcı veritabanı yerine bellekte.
> - **inventory-service** stok takibi, sipariş anında ürünü ayırma.
> - **payment-service** ödeme — Iyzico'ya bağlandı.
> - **order-service** sipariş akışını yönetiyor — burada saga var.
> - **notification-service** sipariş onayı gibi mesajları gönderiyor.
> - **recommendation-service** öneri motoru, ayrıca yapay zekaya da
>   açılıyor.
> - **catalog-stream-service** alternatif hızlı okuma yolu.
> - **seller-service** satıcı kayıtları, listemeleri, yorumlar.
>
> 11 servis çok gibi görünebilir ama mantığı şu: bir kısmı çökerse
> diğerleri çalışmaya devam ediyor. Mesela ödeme servisi kapansa
> ürün gezme akışı bozulmuyor. Buna **microservice** mimarisi deniyor.
> Aslında bu büyüklükte bir proje için fazlasıyla detaylı, tek monolith
> uygulama da yeterdi. Ben **microservice'i öğrenmek için** bilinçli
> olarak bu yapıyı seçtim."

**Tıkla:** `infrastructure/` klasörünü aç.

**Söyle:**

> "Burada 3 yardımcı servis var. Bunlar asıl iş yapmıyor ama servislerin
> birbirini bulmasını sağlıyor:
>
> - **api-gateway** dışarıdan gelen tüm isteklerin tek giriş kapısı.
>   JWT kontrolü burada, sonra hangi servise gideceği burada
>   kararlaştırılıyor.
> - **discovery-server** hangi servis nerede çalışıyor haritası.
>   Eureka kullandım — Spring'in standart aracı.
> - **config-server** bütün servislerin ayar dosyaları burada
>   toplanıyor."

**Tıkla:** `shared/common/` klasörünü aç.

**Söyle:**

> "Tüm servisler aynı yardımcı kodu kullanıyor — hata mesajı formatı,
> response zarfı, log filter'ları. Tekrar tekrar yazmamak için ortak bir
> kütüphaneye topladım. Burada duruyor."

**Tıkla:** Kök klasördeki üç compose dosyasını göster:
`docker-compose.yml`, `docker-compose.prod.yml`, `docker-compose.infra.yml`.

**Söyle:**

> "Üç tane compose dosyası var, niye olduğunu açıklayayım — sıkça
> sorulan bir soru:
>
> **`docker-compose.yml`** ana dosya. Birisi repo'yu klonlar, tek komut
> der: `docker compose up --build`. Hiçbir şey kurmasına gerek yok, 10
> dakikada her şey ayağa kalkıyor.
>
> **`docker-compose.prod.yml`** sunucuya deploy ederken kullanılıyor —
> bu sefer kod build etmiyor, GitHub'tan hazır image'ları çekiyor. Çok
> daha hızlı.
>
> **`docker-compose.infra.yml`** geliştirme için. Sadece veritabanı, cache
> gibi altyapıyı açıyor, servisleri ben IDE'den çalıştırıyorum. Kodu
> değiştirip test ederken çok pratik."

**Tıkla:** `aws/` klasörünü aç.

**Söyle:**

> "AWS'te de çalışsın diye ayrı bir deploy paketi var. RDS ve ElastiCache
> kullanan farklı bir compose dosyası, Beanstalk ayarları, adım adım
> rehber."

**Tıkla:** `docs/` klasörünü aç.

**Söyle:**

> "Dokümanlar burada — README, üretim ortamı kontrol listesi, demo
> scripti, hatta API koleksiyonu. `scripts/seed/` altında demo verisi
> üreten Node script'leri var."

**Söyle (toparla):**

> "Kısacası: 11 backend servisi, 3 altyapı servisi, ortak kütüphane,
> üç farklı senaryoda çalışan compose dosyaları, AWS deploy paketi
> ve dokümantasyon. Şimdi siteyi canlı kullanırken anlatmaya başlayalım."

---

### 2:00 – 3:30 · Müşteri akışı

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

### 3:30 – 4:30 · Satıcı olma akışı

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

### 4:30 – 5:15 · Para akışı

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

### 5:15 – 6:15 · Bir siparişin yolculuğu (★)

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

### 6:15 – 7:00 · İzleme paneli

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

### 7:00 – 7:45 · Yapay zeka entegrasyonu (★)

**Tıkla:** Claude Desktop / Claude Code'a geç

**Söyle:**

> "Bir bonus — projeye yapay zeka erişimi ekledim. Claude Desktop, Cursor
> gibi yapay zeka asistanları artık doğrudan benim ürünlerimi
> sorgulayabilir."

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

### 7:45 – 8:30 · Nasıl çalıştırılır

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

### 8:30 – 9:15 · Geçmiş

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

### 9:15 – 10:00 · Kapanış

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
