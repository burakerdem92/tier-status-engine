# Varsayımlar ve kapsam kararları

## İş kuralları

1. İlk kez görülen üye `CLASSIC` statüsüyle oluşturulur.
2. Statü dönemi takvim yılıdır.
3. Yuvarlama `BigDecimal` ve `RoundingMode.HALF_UP` ile yapılır.
4. TK minimum 500 mil garantisi yalnız `operatingCarrier=TK`, oran pozitif ve hesaplanan sonuç 500'ün altındaysa uygulanır.
5. Reversal orijinal olayın aktivite tarihine yazılır.
6. Normal hareketlerde anlık eşik değerlendirmesi yükselme veya uzatma üretir. Aktif dönem iptali, iptalden sonra artık hak edilmeyen bir yükselmeyi aynı işlemde geri alabilir; ayrı bir manuel eksi düzeltme ise statüyü anında düşürmez. Süre sonundaki yenileme ve bir kademe düşüş kuralı ayrıca uygulanır.
7. Bir hareket birden çok eşiği geçirirse üye doğrudan hak ettiği en yüksek statüye yükselir.

## Geç olay politikası

Vaka geçmiş hakların geri alınması konusunda kararı adaya bırakmaktadır. Prototip müşteri dostu ileriye dönük düzeltme uygular:

- Her olay gerçek aktivite tarihinin dönem toplamını düzeltir.
- Pozitif geç olayın statü geçerlilik penceresi devam ediyorsa güncel statü hemen yükseltilir.
- Tamamen geçmişte kalmış bir pozitif hak tarihsel ledger'da görünür, güncel statüyü değiştirmez.
- Aktif döneme ait orijinal hareket sonradan iptal edilirse dönem toplamı yeniden değerlendirilir. İptal edilen işlemin sağladığı yükselme artık hak edilmiyorsa statü hak edilen kademeye iner. Önceki dönemde kazanılmış ve bugün hâlâ geçerli bir statü korunur; statü düzeltmesi `DOWNGRADE` geçmişi ve outbox olayı üretir.
- Negatif geç olay geçmişte kullanılmış lounge, bagaj veya öncelik faydasını geri almaz.
- Dönem sonu henüz yapılmadıysa değerlendirme düzeltilmiş toplamı kullanır. Daha önce verilmiş dönem sonu kararı geçmiş negatif olay nedeniyle otomatik geri alınmaz.
- Orijinal olaydan önce gelmiş tam iptal, orijinal olayla aynı işlemde netleştirilir; sıfır net mil, geçici yükseltme üretmez.
- Bir orijinal hareket için ikinci tam iptal reddedilir ve DLT'de incelenir.

Üretimde bu politika ürün, hukuk ve müşteri deneyimi ekipleri tarafından onaylanmalıdır. Tam tarihsel yeniden hesaplama gerektiğinde `raw_event` kaynağından gölge hesaplama ve telafi yayını yapılabilir.

## Hatalı mesajlar

- Parse veya şema hatası akışı durdurmaz.
- Ham içerik, hash, topic, partition ve offset ile `raw_event` içinde tutulur.
- DLT mesajı hata açıklamasını ve ham içeriği taşır.
- `eventId` bulunmayan bozuk mesajlar `topic:partition:offset` ile benzersiz tutulur.
- JSON değeri `null` olan Kafka kaydı metadata'sıyla `INVALID` saklanır; içerik alanı boş string olur ve asıl değerin null olduğu hata alanında belirtilir.
- İzin verilen uzunluğu aşan `eventId` ham JSON'da korunur; indeks alanına yazılmaz. Aynı `eventId` farklı içerikle gelirse ikinci ham içerik ayrıca `INVALID` olarak saklanır.
- Veritabanı kesintisinde giriş tüketicisi mesajı onaylamadan yeniden dener. Kesintinin Kafka retention süresinden uzun sürmesi ayrı bir operasyonel risk ve alarm konusudur.

## Referans verisi

- Vaka için referans veritabanı aynı PostgreSQL instance'ında ayrı `reference_data` şemasıyla simüle edilmiştir.
- Oran bulunamazsa olay hesaplanmaz. Kafka error handler üstel aralıklarla tekrar dener ve sonunda DLT'ye yollar.
- Ham olay saklandığı için Kafka retention süresi geçse bile kontrollü yeniden işleme mümkündür.
- Toplu replay ve telafi çalıştıran bir uç nokta/iş mevcut değildir. Üretim tasarımında raw_event verisini ayrı runId ile gölge hesaplamaya alıp fark raporuyla onaylanan düzeltme hareketleri yayımlanacaktır.
- Ayrı referans veri servisine geçişte circuit breaker, kısa zaman aşımı ve yarı açık durumdaki kontrollü denemeler önerilir; prototipte uygulanmamıştır.

## Taşınabilirlik

- Domain kodu veri tabanı özelliklerine bağlı değildir.
- Kimlikler `VARCHAR(36)` UUID olarak saklanır.
- Prototip migration'ı PostgreSQL içindir. Oracle hedefinde `TEXT` alanları `CLOB`, tarih tipleri kurum standardı ve indeks/partition DDL'i Oracle karşılıklarıyla değiştirilir.
- Yüksek hacimli üretim ortamında tablo partition DDL'leri ortam özel Flyway lokasyonlarında tutulmalıdır.

## Trafik ve kural değişikliği sınırları

- Outbox tek uygulamada sıralı Kafka onaylarını bekler. Varsayılan 500 kayıt ve 100 ms bekleme, 300/s olağan hız veya 10.000/s tepe yük için kapasite kanıtı değildir. Consumer lag, outbox yaşı ve veritabanı yüküne göre ayrı publisher ölçeklemesi gerekir.
- Aynı üyeye ait farklı kaynak partition'lardan gelen olayların kaynak sırası bilinmez; iç topic, veritabanına alınmış olayların üye anahtarlı işlenmesini sağlar. Tarihsel negatif düzeltmeler geçmişte verilmiş hakları koruduğu için karar politikası olay sırasına duyarlıdır; kaynak sıra numarası sağlanırsa sıra bundan türetilmelidir.
- Kazanım oranları aktivite tarihine göre seçilir. Tarihsel oran satırlarının değişmezliği exact replay için gereklidir; kod eşikleri `Tier` enum'unda sabittir. Gelecek dönemin farklı eşikleri için etkin tarihli kural tablosu ve kararda kural sürümü gerekir.

## Güvenlik

- Admin dönem sonu endpoint'i `prod` profilinde kapalıdır.
- Örnek secret dosyası gerçek kimlik bilgisi içermez.
- Üretimde REST API kimlik doğrulaması, yetkilendirme, oran sınırlama ve audit log API gateway veya Spring Security ile eklenmelidir.
