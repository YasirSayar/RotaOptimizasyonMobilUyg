# 🗺️ Rota Optimizasyon Mobil Uygulaması

> Gezgin Satıcı Problemi'ni (TSP) **4 farklı algoritma** ile çözen, sonuçları karşılaştırıp en iyisini otomatik seçen Android uygulaması.  
> Yerel satıcılar ve saha ekipleri için müşteri ziyaret rotasını optimize eder.

---

## 📱 Uygulama Ne Yapıyor?

Birden fazla müşteri noktası arasında **en kısa ve en verimli rotayı** otomatik olarak hesaplar.

Tek bir algoritmaya bağlı kalmak yerine, bu proje **dört farklı optimizasyon stratejisini** uygular ve bunları gerçek zamanlı karşılaştırarak en iyi sonucu seçen bir **hibrit mimari** kullanır.

### Temel Özellikler

- 📋 **Müşteri Yönetimi** — Müşteri ekleme, düzenleme, silme, listeleme (CRUD)
- 📍 **Durak Seçimi** — İstenilen müşteriler rota noktası olarak seçilebilir
- 🧬 **4 Farklı Optimizasyon Algoritması** — Genetik Algoritma, 2-opt/3-opt, OR-Tools (LKH-style), Hibrit
- ⚡ **Paralel Çalıştırma** — İki güçlü algoritma eşzamanlı çalışır, sonuçlar karşılaştırılır
- 🗺️ **Rota Görselleştirme** — Optimize edilmiş rota harita üzerinde gösterilir
- 💾 **SQLite Veritabanı** — Müşteri verileri ve mesafe matrisi yerel olarak saklanır ve cache'lenir

---

## 🧬 Optimizasyon Mimarisi

Proje, performans ve sonuç kalitesi arasında denge kurmak için **katmanlı bir algoritma stratejisi** kullanır:

```
┌─────────────────────────────────────────────────┐
│              HybridOptimizer                    │
│   (İki güçlü algoritmayı paralel çalıştırır)     │
└───────────────┬─────────────────┬────────────────┘
                │                 │
       ┌────────▼───────┐ ┌───────▼─────────┐
       │  LKHStyleOpt.  │ │  SimplifiedLKH  │
       │  (OR-Tools)    │ │                 │
       └────────────────┘ └─────────────────┘
                │
        Her ikisi başarısız olursa ↓
                │
       ┌────────▼───────────┐
       │ TwoOptRouteOptimizer│  ← Fallback / hafif cihazlarda kullanılır
       │  (2-opt + 3-opt)    │
       └─────────────────────┘
```

### 1️⃣ Genetik Algoritma (İlk Sürüm)
Popülasyon tabanlı evrimsel yaklaşım: seçilim → çaprazlama → mutasyon döngüsüyle rota uzayını arar. Büyük problemlerde keşif (exploration) gücü yüksek, ancak yakınsama hızı sınırlı.

### 2️⃣ 2-opt / 3-opt İyileştirme (`TwoOptRouteOptimizer`)
- Başlangıç rotasını **Nearest Neighbor** sezgiseliyle kurar
- Rota üzerindeki kenar çiftlerini tekrar tekrar tersine çevirip (`2-opt swap`) iyileştirme arar
- İsteğe bağlı **3-opt** modu, 3 segment üzerinde 4 farklı yeniden bağlama denemesiyle daha güçlü yerel arama yapar
- Hafif ve hızlı — düşük donanımlı cihazlarda **fallback** katmanı olarak kullanılır

### 3️⃣ OR-Tools / LKH-Style Çözücü (`LKHStyleOptimizer`)
Google **OR-Tools** Routing kütüphanesi üzerine kurulu, Lin-Kernighan-Helsgaun (LKH) algoritmasının felsefesinden esinlenen güçlü bir çözücü:
- **Path Cheapest Arc** stratejisiyle akıllı bir başlangıç rotası kurar
- **Guided Local Search** meta-sezgiseli ile yerel minimumlardan kaçar (LKH'nin penaltı tabanlı arama mantığına benzer)
- Zaman sınırlı çalışır (10 / 30 / 60 sn modları) — 100+ duraklı problemlerde bile pratik sürede sonuç verir

### 4️⃣ Hibrit Katman (`HybridOptimizer`)
İki bağımsız algoritmayı (**OR-Tools** ve **SimplifiedLKH**) `ExecutorService` ile **paralel thread'lerde** çalıştırır:
- Her iki sonucu da mesafe ve süre bakımından karşılaştırır
- En kısa mesafeyi veren sonucu otomatik seçer, eşitlik durumunda daha hızlı olanı tercih eder
- İkisi de başarısız olursa **2-opt'a fallback** yapar — sistem hiçbir zaman sonuçsuz kalmaz
- Detaylı, okunabilir log çıktısıyla karşılaştırma sürecini şeffaf hale getirir

> 💡 **Neden hibrit?** Tek bir algoritmaya güvenmek riskli: OR-Tools native kütüphane yüklenemezse veya zaman aşımına uğrarsa, hibrit katman alternatif sonuçla devam eder. Bu, üretim ortamında güvenilirlik için kritik bir tasarım kararıdır.

---

## 🛠️ Kullanılan Teknolojiler

| Katman | Teknoloji |
|--------|-----------|
| Dil | Java (Android) |
| Veritabanı | SQLite (müşteri + mesafe matrisi cache) |
| Optimizasyon | Genetik Algoritma, 2-opt/3-opt, Google OR-Tools (Guided Local Search) |
| Eşzamanlılık | `ExecutorService`, `Future` (paralel algoritma çalıştırma) |
| Mesafe Hesabı | Haversine formülü (coğrafi koordinat mesafesi) |
| Harita | Google Maps API |
| IDE | Android Studio |
| Min SDK | Android 7.0 (API 24) |

---

## 🚀 Kurulum

```bash
# 1. Repoyu klonla
git clone https://github.com/YasirSayar/RotaOptimizasyonMobilUyg.git

# 2. dev branch'ine geç (güncel hibrit optimizer sürümü)
git checkout dev

# 3. Android Studio'da aç

# 4. Google Maps API anahtarını ekle
#    local.properties dosyasına:
MAPS_API_KEY=your_api_key_here

# 5. Çalıştır (emülatör veya fiziksel cihaz)
```

---

## 📂 Proje Yapısı

```
app/src/main/java/com/example/rotaoptjv/
├── model/                     # Müşteri ve Rota veri modelleri
├── db/DatabaseHelper.java     # SQLite veritabanı + mesafe cache yönetimi
├── algorithm/
│   ├── HybridOptimizer.java       # Paralel çalıştırma + sonuç karşılaştırma
│   ├── LKHStyleOptimizer.java     # OR-Tools tabanlı güçlü çözücü
│   ├── SimplifiedLKH.java         # Hafif LKH-tarzı sezgisel
│   └── TwoOptRouteOptimizer.java  # 2-opt / 3-opt + Nearest Neighbor
├── ui/                         # Activity ve Fragment'lar
└── util/                       # Yardımcı sınıflar (Haversine vb.)
```

---

## 💡 Neden Bu Proje?

TSP (Travelling Salesman Problem), bilgisayar biliminin klasik **NP-Hard** problemlerinden biridir.  
Bu proje, tek bir algoritma yazıp bırakmak yerine, **farklı algoritmik yaklaşımların pratikteki trade-off'larını** (hız vs. optimallik, güvenilirlik vs. karmaşıklık) gerçek bir mobil uygulamada test etme fırsatı sunuyor.

Bitirme projesi olarak başlamış, sonrasında **OR-Tools entegrasyonu ve hibrit karşılaştırma katmanıyla** genişletilmiştir — akademik algoritma bilgisini üretim kalitesinde mobil mühendisliğe dönüştürme hedefiyle geliştirilmektedir.

---

## 👨‍💻 Geliştirici

**Yasir Sayar** — Bilgisayar Mühendisi (Fakülte Birincisi, Amasya Üniversitesi 2025)

[![LinkedIn](https://img.shields.io/badge/LinkedIn-0077B5?style=flat&logo=linkedin&logoColor=white)](https://www.linkedin.com/in/yasirsayar/)
[![GitHub](https://img.shields.io/badge/GitHub-100000?style=flat&logo=github&logoColor=white)](https://github.com/YasirSayar)

---

## 📄 Lisans

Bu proje [MIT Lisansı](LICENSE) ile lisanslanmıştır.
