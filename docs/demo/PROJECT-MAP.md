# Proje haritası — neresi ne işe yarıyor

Tek bakışta anlaşılır rehber. Mülakatçı "bu klasörde ne var" diye sorarsa
buradan bakıp 1 cümle anlatabilirsin.

## Ana klasörler

```
springboot-ecommerce/
├── services/              ← 11 backend servisi (asıl iş burada)
├── infrastructure/        ← 3 altyapı servisi (gateway, registry, config)
├── shared/                ← Servisler arası paylaşılan ortak kod
├── docker/                ← Postgres, Prometheus, Grafana ayar dosyaları
├── docs/                  ← Dokümanlar (mülakat scripti dahil)
├── scripts/               ← Demo verisi üretici scriptler
├── aws/                   ← AWS deploy paketi
├── pom.xml                ← Maven (proje tanımı)
├── Dockerfile             ← Backend container yapılandırması
├── docker-compose.yml     ← Tek komutluk full stack
└── README.md              ← Ana doküman
```

---

## services/ — backend servisleri

Her klasör tek bir iş yapan ayrı bir program.

| Klasör | Ne işe yarıyor |
|---|---|
| `user-service` | Kullanıcı kaydı, giriş, JWT token üretimi |
| `product-service` | Ürün kataloğu, kategoriler, fiyat & stok bilgisi |
| `cart-service` | Müşteri sepetini tutuyor (Redis'te) |
| `inventory-service` | Stok yönetimi, sipariş anında stok ayırma |
| `payment-service` | Iyzico ile ödeme alma |
| `order-service` | Sipariş yönetimi, satıcılara komisyon hesaplama |
| `notification-service` | Sipariş onayı gibi mesajları gönderiyor (Slack, email) |
| `recommendation-service` | "Sana özel öneriler" + yapay zeka entegrasyonu |
| `catalog-stream-service` | Hızlı ürün listesi (alternatif okuma yolu) |
| `seller-service` | Satıcı kayıtları, ürün listemeleri, yorumlar |

> Her servisin içinde:
> - `src/main/java/...` → Java kodu
> - `src/main/resources/db/migration/` → Veritabanı tabloları
> - `pom.xml` → bu servisin Maven dosyası

---

## infrastructure/ — altyapı

Asıl işi yapmayan ama servislerin birbirini bulmasını sağlayan 3 servis.

| Klasör | Ne işe yarıyor |
|---|---|
| `api-gateway` | Tek giriş kapısı: dışarıdan gelen tüm istekler buraya gelir, JWT kontrol eder, doğru servise yönlendirir |
| `discovery-server` | "Hangi servis nerede çalışıyor" haritasını tutar (Eureka) |
| `config-server` | Tüm servislerin ayarları burada toplanır |

---

## shared/common/ — ortak kod

Tüm servislerin kullandığı küçük yardımcı kütüphane. Hata mesajı formatı,
response zarfı (`{success, data, timestamp}`), correlation-id filter'ı.
Tek bir JAR olarak diğer servislere dahil edilir.

---

## docker/ — yapılandırma dosyaları

Container'ların ihtiyaç duyduğu küçük config'ler.

| Dosya | Ne işe yarıyor |
|---|---|
| `postgres/init.sql` | İlk açılışta Postgres'te 7 ayrı veritabanı + 7 kullanıcı yaratıyor |
| `prometheus/prometheus.yml` | Hangi servislerden metrik toplanacağı |
| `grafana/provisioning/` | Grafana'nın hangi datasource ve dashboard'u kullanacağı |
| `grafana/dashboards/` | Hazır panel dosyaları |

---

## docs/ — dokümanlar

| Klasör/dosya | Ne |
|---|---|
| `demo/` | **Mülakat videosu için her şey:** SCRIPT.md, CHEATSHEET, prep-demo.sh, bookmarks |
| `MCP-USAGE.md` | Yapay zeka entegrasyonu nasıl kullanılır |
| `production-hardening.md` | Gerçek deploy için kontrol listesi (TLS, secrets, vs) |
| `learning/` | Kişisel notlar, fazların özeti |
| `postman/` | API test koleksiyonu |
| `superpowers/` | Faz tasarım ve plan dokümanları |

---

## scripts/ — yardımcı scriptler

| Klasör/dosya | Ne işe yarıyor |
|---|---|
| `seed/seed.js` | 60 ürün, 5 satıcı, 8 müşteri, yorumlar oluşturuyor — videodan önce çalıştır |
| `seed/backfill-images.js` | Görselsiz ürünlere Picsum'dan resim yazıyor |
| `seed/backfill-inventory.js` | Eksik stok satırlarını dolduruyor |
| `smoke-test.sh` | Stack çalışıyor mu hızlı kontrol |

---

## aws/ — AWS deploy paketi

| Dosya | Ne işe yarıyor |
|---|---|
| `docker-compose.yml` | AWS'te çalışacak versiyon (RDS + ElastiCache'e bağlı) |
| `.ebextensions/` | Beanstalk'a özel ayarlar |
| `AWS-DEPLOY.md` | Adım adım AWS deploy rehberi |

---

## .github/workflows/ — CI/CD

GitHub Actions için iki dosya:

| Dosya | Ne yapıyor |
|---|---|
| `ci.yml` | Her push'ta testleri çalıştırıyor (kod kalite kapısı) |
| `cd.yml` | main'e push'ta image'ları otomatik build edip GitHub'a yüklüyor |

---

## Üç compose dosyası — ne zaman hangisi?

| Dosya | Ne zaman kullan |
|---|---|
| `docker-compose.yml` | **Default.** Sıfırdan klon → `docker compose up --build` → her şey çalışır |
| `docker-compose.prod.yml` | Sunucuda deploy ederken — image'ları GitHub'tan çeker, build etmez |
| `docker-compose.infra.yml` | IDE'de geliştirirken — sadece veritabanı vs. açar, servisleri Maven'la sen çalıştırırsın |

---

## Dockerfile

Backend için tek bir generic Dockerfile var. Her servis bu Dockerfile'ı
`MODULE` parametresiyle kullanıyor. 1 build, 13 farklı servis image'ı.

Frontend'in kendi Dockerfile'ı ayrı repoda
(`springboot-ecommerce-frontend`).

---

## Sıkça sorulan: "Bu kadar servis niye?"

Her servisin kendi veritabanı, kendi sorumluluğu var. Mesela ödeme
servisini kapatırsam siteyi gezme akışı bozulmuyor — sadece sipariş
verme aşamasında uyarı veriyor. Bu yapıya **microservice** deniyor.

Avantaj: bir kısmı çökmüş olsa bile diğerleri çalışmaya devam eder,
ekipler ayrı ayrı geliştirip deploy edebilir.

Dezavantaj: küçük projeler için fazla karmaşık. Tek monolith uygulama
da yeterdi. Ben **microservice'i pratik etmek için** bu yapıyı seçtim.

---

## Mülakatta "kodu açayım mı" sorulursa

| Klasör | Ne göstereceksin |
|---|---|
| `services/order-service/.../OrderService.java` | Saga akışı (7 adım sıralı, her birinde compensation) |
| `services/seller-service/.../SubOrderSplitter.java` | Komisyon hesaplama mantığı |
| `infrastructure/api-gateway/.../GatewayJwtAuthenticationFilter.java` | JWT kontrol kodu |
| `services/payment-service/.../iyzico/IyzicoClient.java` | Iyzico SDK kullanımı |

Her biri ~100 satır, hızlıca anlatılır.
