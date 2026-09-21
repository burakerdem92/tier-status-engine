# Vaka maddeleri ve prototipin sınırları

Bu tablo, dört zorunlu görev ile isteğe bağlı görevleri ayrı tutar. Kodun çalıştığını
kanıtlamak için Docker açıkken `mvn clean verify` çalıştırılmalı ve entegrasyon
testlerinde `Skipped: 0` görülmelidir. Test raporları eski çalıştırmalardan kalabilir;
teslim dosyasına `target/` klasörü konulmamalıdır.

| Vaka maddesi | Durum | Kod veya açık sınır |
| --- | --- | --- |
| 1. Her mesajı ham haliyle kaydet; tekrar teslim ve bozuk mesajı yönet | Prototipte var | `IngestionService`, `EventParser`, `RawEventEntity`; DB yazılamazsa giriş listener'ı offset'i onaylamaz. DB kesintisi Kafka retention süresini aşmamalı. |
| 2. Referans oranları, dört olay türü, yuvarlama, minimum TK mili, ledger | Prototipte var | `DatabaseReferenceRateProvider`, `StatusMilesCalculator`, `TierProcessingService`, `mileage_ledger`. Referans verisi aynı PostgreSQL'in ayrı `reference_data` şemasındadır. |
| 3. Dönem toplamı, upgrade, geç olay, erken/geç iptal, yıl sonu, history ve TIER_CHANGED | Prototipte var; sınırları var | `TierProcessingService`, `TierDecisionService`, `PeriodEndService`, `OutboxPublisher`. Aktif dönem iptali hak edilmeyen yükselmeyi geri alır, geçmiş dönem iptali eski statü kararını geri almaz. Kapanış işi milyonlarca üye için henüz bölümlü değildir ve 1 Ocak çalışmazsa otomatik catch-up yapmaz; admin endpoint'iyle tekrar tetiklenebilir. |
| 4. Statü, history ve ledger REST sorguları | Prototipte var | `TierQueryController`, `TierQueryService`; liste sonucunda en çok 200 satır, cursor pagination yoktur. |
| Dağıtık kilit ve partition anahtarı karşılaştırması | Kısmen var | İç topic `memberId` key kullanır, mevcut satırlar kilitlenir. Kaynak sıra numarası yoktur; yeni üyenin ilk satırı eşzamanlı oluşturulurken unique ihlali yeniden deneme gerektirir. |
| DB/Kafka arasında iş etkisinin tekilleştirilmesi | Prototipte var; at-least-once yayın | DB transaction + benzersiz anahtar + outbox. Çıkış olayını alan sistem aynı `eventId` ile idempotent işlemelidir. |
| Back-pressure ve consumer lag | Tasarım önerisi | Lag, en eski mesaj yaşı, outbox backlog ve DB pool izlenmeli; tüketici kapasitesi ölçülmeli. Otomatik pause/resume uygulanmamıştır. |
| Referans kesintisi, poison mesaj, retry, DLT, circuit breaker | Kısmen var | Retry/DLT var. Eksik oran DLT'ye gider; ayrı referans kaynağına geçildiğinde circuit breaker gerekir, prototipte yoktur. |
| Hatalı kurallar için replay ve telafi | Tasarım önerisi | Ham kayıt var; otomatik toplu replay, gölge hesap ve telafi yayını yoktur. |
| Yüksek hacim, tablo partitioning ve arşiv | Tasarım önerisi | İndeks ve örnek partition planı var; gerçek partition/arşiv ve 8.000–10.000/s yük testi yoktur. Tek publisher Kafka onaylarını sırayla bekler; gerçek kapasite ölçülmelidir. |
| Kubernetes health, limit, autoscale ve metrikler | Kısmen var | `deploy/openshift` API/consumer ayırır, readiness/liveness ve resource limitleri var. API için CPU HPA var; tüketici için lag bazlı HPA/KEDA manifesti yoktur. |
| API ve tüketici yük izolasyonu | Örnek yapı var | Aynı imaj farklı bayraklarla iki deployment olarak çalışır; outbox ve dönem sonu işi consumer deployment'ıyla paylaşılır. |
| Gelecek dönemde eşik/oran değişikliği | Kısmen var | Oranlar etkin tarihle seçilir, geçmiş satırlar değişmez kalmalıdır. Eşikler `Tier` enum'unda sabit, sürümlü eşik tasarımı henüz uygulanmamıştır. |

## Gecikmiş olay politikası

Pozitif olay geç gelirse aktivite yılının toplamını düzeltir; hak ettiği
statünün geçerlilik süresi halen devam ediyorsa güncel statü yükseltilir.
Kapanmış dönemin negatif düzeltmesi muhasebe hareketini ve toplamı düzeltir,
geçmişte verilmiş statü kararını otomatik geri almaz. İptal orijinal olaydan
önce biliniyorsa ikisi aynı işlemde netleştirilir; sıfır net mil statü kazandırmaz.
Aktif dönemde iptal daha sonra gelirse hak edilmeyen yükselme geri alınır:
23.000 + 5.000 mil ile Classic Plus olan üye uçuşun iptalinde 23.000 mile
ve Classic'e döner. Daha eski dönemden geçerli bir statü varsa korunur.
Negatif manuel düzeltme aynı iptal politikasıyla otomatik düşüş başlatmaz.
Tek orijinal harekete ikinci tam iptal kabul edilmez. Bu politika bir ürün kararıdır;
farklı kaynak partition'larından gelen olayların ilk geliş sırası garanti değildir.
