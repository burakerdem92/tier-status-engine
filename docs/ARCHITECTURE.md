# Mimari ve tasarım kararları

## Modül sınırları

Kod tek deploy edilebilir uygulama içinde belirgin sorumluluklara ayrılmıştır:

- `ingestion`: Ham Kafka mesajını saklar, doğrular ve iç topic outbox kaydını üretir.
- `service`: Framework bağımlılığı düşük hesaplama ve statü karar akışını yürütür.
- `persistence`: Append-only ledger, dönem toplamı, statü geçmişi ve outbox verisini saklar.
- `outbox`: Veri tabanında commit edilen mesajları Kafka'ya güvenli biçimde yayınlar.
- `api`: Salt-okunur statü ve hareket sorgularını sunar.

## Neden iki Kafka aşaması var?

Kaynak sistemlerin aynı üyeyi aynı partition'a yazacağı garanti edilmemektedir. Doğrudan paralel tüketim, iki instance'ın aynı üye toplamını aynı anda güncellemesine ve lost update oluşmasına yol açabilir.

İlk tüketici hesaplama yapmaz. Ham veriyi sakladıktan sonra mesajı `memberId` key'iyle iç topic'e yazar. Kafka bu noktadan sonra aynı üyeyi aynı partition'da tutar. Yine de rebalance ve operasyonel müdahaleler için `member_period` ve `member_status` güncellemelerinde veri tabanı kilidi ve optimistic version son savunma olarak durur.

## Idempotency

| Katman | Benzersiz anahtar | Koruduğu durum |
|---|---|---|
| Ham olay | `event_id` | Aynı iş olayının tekrar kaydı |
| Kafka teslimi | `topic:partition:offset` | Aynı teslimin tekrar işlenmesi |
| Ledger | `source_event_id` | Aynı mil etkisinin ikinci kez oluşması |
| Statü kararı | `decision_key` | Aynı upgrade/renewal/downgrade kararının tekrarı |
| Outbox | `id` | Tüketici tarafında dedup için sabit yayın kimliği |

Aynı `eventId` farklı payload ile gelirse ilk olay korunur ve çakışma DLT'ye bildirilir.

## Transaction sınırları

Ingestion transaction'ı:

```text
raw_event insert + member-events outbox insert
```

Tier processing transaction'ı:

```text
ledger insert + member_period update + member_status update
+ tier_history insert + tier-changed outbox insert
```

Kafka offset'i listener başarıyla döndükten sonra commit edilir. Commit öncesi çökme mesajın tekrar gelmesine neden olabilir; benzersiz anahtarlar ikinci iş etkisini engeller.

## Outbox yayınlama

Publisher PENDING satırları pessimistic lock ile claim eder. Yayın başarılıysa PUBLISHED, hata halinde üstel backoff ile tekrar PENDING olur. PROCESSING durumunda takılı kalan satırlar süre aşımından sonra yeniden claim edilebilir.

Bu mekanizma at-least-once yayın yapar. `TIER_CHANGED.eventId` karar anahtarından deterministik üretildiği için downstream tüketicisi aynı id ile dedup yapabilir.

## Reversal-before-original

Orijinal ledger hareketi yoksa reversal kaybolmaz:

1. Ham reversal saklanır.
2. `pending_reversal` satırı oluşur.
3. Orijinal olay geldiğinde pozitif hareket yazılır.
4. Aynı transaction içinde reversal kadar negatif hareket eklenir.
5. Net dönem etkisi sıfır olur.

Statü kararı, orijinal hareket ve önceden bekleyen iptal aynı transaction içinde netleştirildikten sonra bir kez değerlendirilir. Tek orijinal harekete ikinci tam iptal uygulanmaz.

Orijinal hareket aktif dönemde önce işlenip daha sonra iptal edilirse negatif hareketten sonraki toplam yeniden değerlendirilir. Hak edilmeyen aktif dönem yükselmesi `DOWNGRADE` olarak history ve çıkış outbox'ına aynı işlemde yazılır. Varsa önceki dönemden kazanılmış halen geçerli statü ve onun bitiş tarihi korunur. Önceki dönem iptali geçmişte verilmiş statüyü otomatik geri almaz; ayrıca negatif manuel düzeltme doğrudan iptal sayılmadığı için otomatik düşüş tetiklemez.

## Kural sürümü

Oranlar `effective_from`, `effective_to` ve `rule_version` ile sürümlenir. Seçim eventTime'a göre değil aktivite tarihine göre yapılır. Ledger kullanılan sürümü kaydeder. Geçmiş oran satırları değişmez tutulursa aynı sürüm yeniden bulunabilir. Uygulama şu an oran değerinin tam snapshot'ını veya eşik sürümünü ledger'da tutmaz; otomatik replay mevcut değildir.

## Yük izolasyonu

`CONSUMER_ENABLED`, `API_ENABLED`, `OUTBOX_ENABLED` ve `PERIOD_END_ENABLED` değişkenleri aynı imajı farklı rollerde çalıştırır. OpenShift örneği consumer ve API'yi ayrı deployment, kaynak limiti ve connection pool ile ayırır.

## İsteğe bağlı üretim tasarımı

**Üye eşzamanlılığı:** Kaynakların hepsini `memberId` ile aynı topic'e yazdırmak en sade çözüm olurdu; kaynak anahtarları bizim kontrolümüzde değildir. Yalnız veritabanında üyeye göre kilit almak tüm trafiği ilk tüketicide seri hale getirir ve kilit rekabetini artırır. Bu yüzden iki aşama ve ikinci topic'te `memberId` anahtarı seçildi. Mevcut satırlar pessimistic lock ve version ile korunur. Yeni üyenin ilk dönem/statü satırını iki süreç aynı anda eklemeye çalışırsa unique hatası yeniden denenebilir; bu durum için ayrı yük/yarış testi yoktur. Farklı kaynak partition'larındaki olayların orijinal sırası bilinmediğinden mutlak tarih sırası ancak kaynakların bir üye sıra numarası vermesiyle kurulabilir.

**İş etkisinin bir kez oluşması:** Kafka producer idempotence ağ içi tekrarları azaltır, fakat ilişkisel DB ile Kafka'yı tek transaction yapmaz. Girişte ham olay + iç-topic outbox kaydı, hesaplamada ledger + statü + history + çıkış outbox kaydı kendi DB transaction'larında yazılır. Outbox tekrar yayınlayabilir; ledger `source_event_id`, history `decision_key`, çıkış mesajı ise deterministik `eventId` ile tekilleştirilir. `tier-changed` tüketicileri aynı eventId'yi tekrar uygulamamalıdır. Bu, uçtan uca tek Kafka teslimi değil, tek iş etkisi hedefidir.

**Lag ve back-pressure:** `status-events` ile `member-events` lag'ini ve en eski bekleyen mesajın yaşını ayrı ölçmek gerekir. DB pool dolarsa tüketici concurrency'si sınırlandırılıp partition tüketimi geçici pause/resume ile yavaşlatılabilir; outbox birikimi ve 95. yüzdelik statü yansıma süresi alarm üretmelidir. Ölçekleme partition sayısını aşamaz; Kafka retention'a kalan süre lag'in eritilme süresinden büyük tutulmalıdır. Otomatik pause/resume veya lag bazlı HPA bu prototipte bulunmaz.

**Hata ayırımı:** Girişte doğrulama hatası ham `INVALID` kaydına ve DLT outbox'ına gider; DB yazma hatası offset commit edilmeden yeniden denenir. Hesaplama tarafında kısa süreli hata üstel gecikmeyle denenir, ardından ham olay saklı kalırken Kafka DLT'ye gider. Üretimde ayrı referans kaynağı için kısa timeout ve circuit breaker gerekir: tekrarlayan bağlantı hatasında devre açılır, bekleme sonrası sınırlı half-open sorgusu yapılır, bağlantı düzelince yeniden kapanır. Şema/iş kuralı hataları için kör retry yerine DLT ve inceleme gerekir. Prototipte circuit breaker ve otomatik DLT replay yoktur.

**Yanlış kuralın düzeltilmesi:** Önerilen operasyon, `raw_event` kayıtlarını etkilenen dönem/üye/runId ile seçmek, tarihsel oran ve eşik sürümünü sabitleyerek gölge tabloda yeniden hesaplamak, mevcut ledger ve history ile farkı raporlamak, inceleme sonrası yeni ve idempotent düzeltme olayları yayımlamaktır. Eski hareketler silinmez; telafi ve yeni `TIER_CHANGED` outbox'a aynı DB transaction'ında yazılır. Gölge hesaplama ve onay akışı henüz kodlanmamıştır.

**Veri büyümesi ve dönem sonu:** Üretimde raw ve ledger hareketleri dönem yılına göre partition edilmeli, sıcak sorgu için `(member_id, activity_date)` indeksi tutulmalı, kapanan dönemlerin ham JSON'u denetim politikasına uygun arşivlenmelidir. Dönem sonu değerlendirmesi şu an tek DB transaction'ında bütün süresi dolan üyeleri tarar. Scheduler 1 Ocak çalışmazsa otomatik catch-up yapmaz; demo admin endpoint'iyle eksik tarih tekrar tetiklenebilir. Milyonlarca üye için üye aralıklarına bölünmüş checkpoint'li işler, tekrar çalışabilir karar anahtarı ve dönem kapanış saatine göre geç olay politikası gerekir.

**OpenShift:** Örnek manifestlerde API ve consumer ayrı deployment, readiness/liveness ve CPU/bellek limitleri vardır. API HPA CPU'ya bakar; consumer için consumer lag ve oldest-event-age ile KEDA/HPA planlanmıştır, manifesti yoktur. İzlenecek metrikler işlem hızı, hata/DLT oranı, consumer lag, en eski olay yaşı, outbox pending yaşı, DB pool kullanımı, statü güncelleme gecikmesi ve API 95. yüzdelik yanıt süresidir. Kazanım oranları aktivite tarihine göre sürümlüdür; dönem bazlı statü eşikleri prototipte `Tier` enum'unda sabittir ve yeni döneme geçmeden önce etkin tarihli tabloya taşınmalıdır.
