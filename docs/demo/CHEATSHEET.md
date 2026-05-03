# Cheatsheet — kayıt sırasında 2. ekranda açık tut

## Tab sırası (`Cmd+Tab` ile geç)

| # | URL | Niçin |
|---|---|---|
| 1 | http://localhost | Storefront |
| 2 | http://localhost/demo | Login switcher |
| 3 | http://localhost/admin/payouts | Admin |
| 4 | http://localhost:3000 | Grafana (admin/admin) |
| 5 | http://localhost:9411 | Zipkin |
| 6 | https://github.com/eaaslan/springboot-ecommerce | Repo |

## Hesap bilgileri (hepsi `password123`)

| Email | Rol | Sonra git |
|---|---|---|
| `alice@example.com` | ADMIN | /admin/products |
| `seller1@example.com` | TechMart | /seller/dashboard |
| `seller2@example.com` | Moda Home | /seller/dashboard |
| `buyer1@example.com` | buyer | / |

## Kart bilgileri

| Kart | Sonuç |
|---|---|
| `4111111111111111` | mock branch: SUCCESS |
| `4111111111111115` | mock branch: DECLINED |
| `5528790000000008` | Iyzico sandbox: SUCCESS |
| `4111111111111115` | Iyzico sandbox: DECLINED |

CVC: `123` · Exp: `12/2030` · Holder: herhangi bir isim

## Anahtar dakika

| Saat | Bölüm |
|---|---|
| 0:00 | Açılış |
| 0:20 | Proje yapısı (IDE) |
| 1:30 | Müşteri akışı (login → cart → checkout) |
| 3:00 | Marketplace (apply → admin approve) |
| 4:00 | Payout |
| 4:45 | **Zipkin trace ★** |
| 5:45 | Grafana |
| 6:30 | **MCP / Claude ★** |
| 7:15 | Deploy story |
| 8:00 | Phase log |
| 9:00 | Kapanış |

## Konuşmada kaçınılacaklar

- ❌ "Kullandık" → ✅ "Bu kararı şu trade-off'la verdim"
- ❌ "Spring Boot çok güzel" → ✅ "13 servis, her biri bağımsız Postgres + Flyway"
- ❌ "X yaptım" → ✅ "X yapma sebebim Y'ydi, alternatif Z idi ama..."
- ❌ Sessizlik → ✅ "Şimdi şuraya bakalım..."

## Acil durum kombosu

```bash
# Stack tamamen reset
docker compose down -v && docker compose up --build -d
./docs/demo/prep-demo.sh
```

10 dk + 30sn boot. Kayıttan **45 dk önce** çalıştır.
