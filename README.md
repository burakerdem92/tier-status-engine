# Tier Status Engine

Miles\&Smiles statü millerini Kafka olaylarından hesaplayan, hareket geçmişini
saklayan ve statü değişikliklerini yeniden Kafka'ya yayınlayan Spring Boot
prototipidir.

Bu README iki çalışma biçimini anlatır:

1. **Önerilen yöntem — yalnız Docker:** Java, Maven veya IDE kurmadan uygulama,
PostgreSQL ve Kafka birlikte çalıştırılır. Demo tamamen Swagger UI üzerinden
yapılabilir.
2. **İsteğe bağlı yöntem — IntelliJ:** PostgreSQL ve Kafka Docker'da, Spring Boot
uygulaması IntelliJ'de çalıştırılır.

> Projedeki statü eşikleri ve kazanım oranları vaka için oluşturulmuş örnek
> değerlerdir; gerçek Miles\\\&Smiles kuralları değildir.

## 1\. Gereksinimler

### Yalnız Docker ile çalıştırmak için

* Docker Desktop veya Docker Engine
* Docker Compose v2 (`docker compose` komutu)
* İlk image build'inde bağımlılıkların indirilebilmesi için internet erişimi
* Boş olması gereken yerel portlar: `5432`, `9092`, `8080`

Bu yöntemde **Java, Maven, IntelliJ veya DBeaver kurulması gerekmez**.

Docker Desktop kullanılıyorsa uygulamayı başlatmadan önce Docker Desktop'ı açın
ve engine'in çalışır duruma gelmesini bekleyin. Windows'ta
`Virtualization support not detected` hatası alınırsa BIOS/UEFI virtualization
ve Windows WSL 2/Virtual Machine Platform ayarlarının etkinleştirilmesi gerekir.

### IntelliJ ile çalıştırmak için

Docker gereksinimlerine ek olarak:

* JDK 21
* IntelliJ IDEA
* Komut satırından Maven kullanılacaksa Maven 3.9+

## 2\. Proje yapısı

|Yol|İçerik|
|-|-|
|`src/main/java`|Uygulama kaynak kodu|
|`src/main/resources/db/migration`|Flyway şema ve referans veri kurulumu|
|`src/test`|Unit ve Testcontainers testleri|
|`scripts`|Kafka'ya doğrudan gönderilebilecek örnek olaylar|
|`docs`|Mimari, varsayımlar ve kontrol listesi|
|`deploy/openshift`|OpenShift/Kubernetes deployment örnekleri|
|`docker-compose.yml`|PostgreSQL, Kafka ve uygulamanın tek komutla kurulumu|

## 3\. Önerilen kurulum: Uygulamayı tamamen Docker ile çalıştırma

Terminali ZIP'ten çıkarılmış projenin kök klasöründe açın. Bu klasörde
`pom.xml` ve `docker-compose.yml` görünmelidir.

### 3.1. Tüm sistemi başlatma

```bash
docker compose up --build -d
```

İlk kurulum, Maven bağımlılıkları ve Docker image'ları indirileceği için birkaç
dakika sürebilir. Servis durumlarını kontrol edin:

```bash
docker compose ps
```

Beklenen servisler:

|Servis|Görevi|Beklenen durum|
|-|-|-|
|`postgres`|Uygulama ve referans veritabanı|`healthy`|
|`kafka`|Giriş, üyeye göre sıralama ve çıkış topic'leri|`healthy`|
|`app`|Spring Boot API ve Kafka consumer'ları|`healthy`|

`app` henüz healthy değilse logları inceleyin:

```bash
docker compose logs -f app
```

Log takibinden çıkmak için `Ctrl+C` kullanılır; container çalışmaya devam eder.

### 3.2. Sağlık kontrolü ve Swagger UI

Tarayıcıda sırasıyla açın:

* Readiness: [http://localhost:8080/actuator/health/readiness](http://localhost:8080/actuator/health/readiness)
* Swagger UI: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

Readiness yanıtında `"status":"UP"` görüldüğünde sistem demoya hazırdır.
Swagger'da aşağıdaki üç grup görünmelidir:

* `demo-event-controller`: Olayı Kafka'ya gönderir.
* `tier-query-controller`: Güncel statü, history ve ledger sorgularını yapar.
* `admin-controller`: Dönem sonu işlemini elle tetikler.

`demo-event-controller` görünmüyorsa uygulama demo özelliği kapalı başlamıştır.
Docker Compose içindeki `DEMO\\\_ENABLED: "true"` değerini ve `app` container'ının
çalıştığını kontrol edin.

## 4\. Swagger UI ile adım adım ana demo

Bu senaryo tek bir üyenin:

1. İlk uçuş sonunda 23.000 mil ile `CLASSIC` kalmasını,
2. İkinci uçuşla 28.000 mile çıkıp `CLASSIC\\\_PLUS` olmasını,
3. İkinci uçuş iptal edildiğinde 23.000 mile ve `CLASSIC` statüsüne dönmesini

gösterir. Demo mesafeleri eşik davranışını kısa biçimde göstermek için sentetik
seçilmiştir.

Her olay için Swagger'da `POST /api/v1/demo/events` satırını açın, **Try it
out** düğmesine basın, JSON'u yapıştırın ve **Execute** seçeneğini kullanın.

> `202 ACCEPTED\\\_BY\\\_KAFKA`, olayın Kafka'ya başarıyla yazıldığını gösterir.
> İşleme asenkrondur; sorgudan önce 1–3 saniye bekleyin.

### 4.1. Birinci uçuş — müşteri Classic kalır

```json
{
  "eventId": "demo-standard-flight-1",
  "eventType": "FLIGHT\\\_FLOWN",
  "eventTime": "2026-09-21T09:00:00Z",
  "source": "DCS",
  "memberId": "TKDEMO\\\_STANDARD",
  "payload": {
    "marketingCarrier": "TK",
    "operatingCarrier": "TK",
    "flightNumber": "1001",
    "origin": "IST",
    "destination": "SIN",
    "flightDate": "2026-09-21",
    "cabin": "ECONOMY",
    "bookingClass": "Y",
    "distanceMiles": 23000,
    "ticketNumber": "2350000000001",
    "couponNumber": 1
  }
}
```

Ardından `GET /api/v1/members/{memberId}/tier` endpoint'ini çalıştırın:

* `memberId`: `TKDEMO\\\_STANDARD`

Beklenen temel alanlar:

```json
{
  "tier": "CLASSIC",
  "statusMilesInPeriod": 23000,
  "nextTier": "CLASSIC\\\_PLUS",
  "milesToNextTier": 2000
}
```

### 4.2. İkinci uçuş — Classic Plus upgrade

Yine `POST /api/v1/demo/events` üzerinden gönderin:

```json
{
  "eventId": "demo-standard-flight-2",
  "eventType": "FLIGHT\\\_FLOWN",
  "eventTime": "2026-09-21T12:00:00Z",
  "source": "DCS",
  "memberId": "TKDEMO\\\_STANDARD",
  "payload": {
    "marketingCarrier": "TK",
    "operatingCarrier": "TK",
    "flightNumber": "1002",
    "origin": "IST",
    "destination": "LHR",
    "flightDate": "2026-09-21",
    "cabin": "ECONOMY",
    "bookingClass": "Y",
    "distanceMiles": 5000,
    "ticketNumber": "2350000000002",
    "couponNumber": 1
  }
}
```

1–3 saniye sonra aynı tier sorgusunu tekrarlayın. Beklenen sonuç:

```json
{
  "tier": "CLASSIC\\\_PLUS",
  "statusMilesInPeriod": 28000,
  "nextTier": "ELITE",
  "milesToNextTier": 12000
}
```

### 4.3. İkinci uçuşun iptali — Classic'e düzeltme

`POST /api/v1/demo/events` üzerinden gönderin:

```json
{
  "eventId": "demo-standard-reversal-2",
  "eventType": "ACCRUAL\\\_REVERSAL",
  "eventTime": "2026-09-21T15:00:00Z",
  "source": "REVENUE\\\_ACCOUNTING",
  "memberId": "TKDEMO\\\_STANDARD",
  "payload": {
    "originalEventId": "demo-standard-flight-2",
    "reason": "TICKET\\\_REFUNDED"
  }
}
```

Tier sorgusunu tekrar çalıştırın. Beklenen sonuç 23.000 mil ve `CLASSIC`tir.
Aktif dönemde iptal edilen uçuş upgrade'i artık desteklemediği için statü de
düzeltilir.

### 4.4. History ve ledger ile sonucu kanıtlama

`GET /api/v1/members/{memberId}/tier-history`:

* `memberId`: `TKDEMO\\\_STANDARD`
* `from`: `2026-01-01`
* `to`: `2026-12-31`
* `limit`: `100`

History endpoint'i en yeni kararı önce döndürür. Bu nedenle listede önce
`DOWNGRADE`, ardından daha eski `UPGRADE` kararı görünür.

`GET /api/v1/members/{memberId}/mileage-ledger` için aynı parametreleri
kullanın. Ledger'da aşağıdaki hareketler görünür:

|Olay|Statü mili|
|-|-:|
|Birinci uçuş|`+23000`|
|İkinci uçuş|`+5000`|
|İkinci uçuş iptali|`-5000`|
|Net|`23000`|

### 4.5. Duplicate/idempotency kontrolü

4.1'deki ilk uçuş JSON'unu **aynı `eventId` ile** tekrar gönderin. İstek Kafka
tarafından kabul edilir ancak iş etkisi ikinci kez oluşmaz. Tier toplamı 23.000,
ledger satır sayısı 3 olarak kalır. Bu, Kafka'nın at-least-once teslimatına
karşı consumer'ın idempotent davrandığını gösterir.

## 5\. Swagger üzerinden ek kısa demolar

Ana senaryo sunum için yeterlidir. Aşağıdaki örnekler yalnız ek teknik kanıt
istenirse çalıştırılabilir. Her örnekte yeni `memberId` ve yeni `eventId`
kullanıldığı için ana senaryodan bağımsızdır.

### 5.1. USD partner işlemi

```json
{
  "eventId": "demo-partner-usd-1",
  "eventType": "PARTNER\\\_ACTIVITY",
  "eventTime": "2026-09-21T10:00:00Z",
  "source": "PARTNER\\\_FILE",
  "memberId": "TKDEMO\\\_PARTNER",
  "payload": {
    "partnerCode": "AURORA\\\_HOTELS",
    "partnerType": "HOTEL",
    "activityDate": "2026-09-21",
    "activityRef": "AUR-DEMO-1",
    "amount": 1140.00,
    "currency": "USD"
  }
}
```

Referans oranı `AURORA\\\_HOTELS/USD = 2` olduğundan beklenen hareket 2.280 statü
milidir. Buradaki `2`, döviz kuru değil, partnerin sözleşmesel statü mili
kazanım oranıdır; prototip ayrıca FX dönüşümü yapmaz.

### 5.2. TK minimum 500 mil kuralı

```json
{
  "eventId": "demo-tk-minimum-1",
  "eventType": "FLIGHT\\\_FLOWN",
  "eventTime": "2026-09-21T11:00:00Z",
  "source": "DCS",
  "memberId": "TKDEMO\\\_MINIMUM",
  "payload": {
    "operatingCarrier": "TK",
    "bookingClass": "P",
    "flightDate": "2026-09-21",
    "distanceMiles": 600
  }
}
```

`TK/P` oranı `%50` olduğu için ham sonuç 300'dür; TK için pozitif kazanımda
minimum kuralı uygulanır ve ledger'a **500 mil** yazılır.

### 5.3. Geçersiz isteğin API doğrulaması

```json
{
  "eventId": "demo-invalid-date-1",
  "eventType": "FLIGHT\\\_FLOWN",
  "eventTime": "not-a-date",
  "source": "DCS",
  "memberId": "TKDEMO\\\_INVALID",
  "payload": {
    "operatingCarrier": "TK",
    "bookingClass": "Y",
    "flightDate": "2026-09-21",
    "distanceMiles": 1000
  }
}
```

Swagger köprüsü mesajı Kafka'ya göndermeden önce aynı parser ile doğruladığı
için bu istek `400 Bad Request` döner. Poison message'ın Kafka consumer
tarafındaki `INVALID`/DLT davranışını göstermek için aşağıdaki doğrudan Kafka
yöntemi kullanılmalıdır.

## 6\. İsteğe bağlı: Kafka'ya doğrudan örnek olay gönderme

Bu bölüm Swagger kullanmak istemeyen veya poison message davranışını test etmek
isteyenler içindir.

### Windows PowerShell

```powershell
Get-Content -Raw .\\\\\\\\scripts\\\\\\\\invalid-message.json |
  docker compose exec -T kafka kafka-console-producer `
  --bootstrap-server kafka:29092 --topic status-events
```

### Bash / Git Bash / WSL

```bash
docker compose exec -T kafka kafka-console-producer \\\\\\\\
  --bootstrap-server kafka:29092 --topic status-events \\\\\\\\
  < scripts/invalid-message.json
```

Birkaç saniye sonra kayıt durumunu Docker içindeki PostgreSQL üzerinden
kontrol edebilirsiniz; ayrıca DBeaver kurulması gerekmez:

```bash
docker compose exec postgres psql -U tierstatus -d tierstatus -c \\\\\\\\
  "select event\\\_id, processing\\\_status, validation\\\_error from raw\\\_event order by received\\\_at desc limit 5;"
```

Geçersiz mesaj `raw\\\_event` tablosunda `INVALID` durumda ve doğrulama açıklamasıyla
saklanır; ayrıca `status-events-dlt` topic'ine bildirilir.

Hazır örneklerin tamamını Bash üzerinden göndermek için:

```bash
./scripts/publish-samples.sh
./scripts/demo-queries.sh
```

## 7\. Docker yaşam döngüsü ve temiz başlangıç

### Sistemi durdurma — veriyi korur

```bash
docker compose down
```

### Sistemi yeniden başlatma

```bash
docker compose up -d
```

### Tüm demo verisini silme — geri alınamaz

```bash
docker compose down -v
docker compose up --build -d
```

`-v`, PostgreSQL volume'ünü ve içindeki tüm demo kayıtlarını siler. Ana Swagger
senaryosunu aynı `eventId` değerleriyle baştan çalıştırmak için bu temiz başlangıç
kullanılabilir.

Sadece uygulama kodu değiştiyse image'ı yeniden oluşturun:

```bash
docker compose up --build -d app
```

## 8\. İsteğe bağlı kurulum: Uygulamayı IntelliJ'den çalıştırma

Bu yöntemde PostgreSQL ve Kafka Docker'da, uygulama ise yerel JVM'de çalışır.

### 8.1. Docker'da yalnız altyapıyı başlatma

Tam Docker kurulumu daha önce çalıştırıldıysa önce Docker'daki uygulamayı
durdurun; aksi halde iki uygulama da `8080` portunu kullanmak ister:

```bash
docker compose stop app
docker compose up -d postgres kafka
```

`docker compose ps` çıktısında `postgres` ve `kafka` healthy, `app` ise durmuş
olmalıdır.

### 8.2. IntelliJ ayarları

1. IntelliJ'de projenin `pom.xml` dosyasını proje olarak açın.
2. Project SDK/JDK olarak **Java 21** seçin.
3. Maven bağımlılıklarının yüklenmesini bekleyin.
4. `TierStatusEngineApplication` sınıfını açın.
5. Run/Debug Configuration içinde **Program arguments** alanına şunu yazın:

```text
   --tier.demo.enabled=true
   ```

6. `TierStatusEngineApplication` sınıfını çalıştırın.
7. Loglarda `Started TierStatusEngineApplication` mesajını gördükten sonra
[http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) adresini açın.

Program argument verilmezse güvenlik amacıyla demo endpoint'i varsayılan olarak
kapalıdır ve Swagger'da `demo-event-controller` görünmez. Docker Compose bunu
`DEMO\\\_ENABLED=true` environment değişkeniyle otomatik açar.

Varsayılan yerel bağlantılar şunlardır:

|Bağımlılık|Değer|
|-|-|
|PostgreSQL URL|`jdbc:postgresql://localhost:5432/tierstatus`|
|Kullanıcı / parola|`tierstatus` / `tierstatus`|
|Kafka bootstrap server|`localhost:9092`|
|Uygulama|`http://localhost:8080`|

Uygulamayı IntelliJ yerine Maven ile çalıştırmak da mümkündür:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--tier.demo.enabled=true
```

Windows PowerShell argümanı ayırırsa şu biçimi kullanın:

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--tier.demo.enabled=true"
```

Yerel uygulamayı kapattıktan sonra tekrar tamamen Docker'a dönmek için:

```bash
docker compose up -d app
```

## 9\. REST API özeti

|Method|Endpoint|Açıklama|
|-|-|-|
|`POST`|`/api/v1/demo/events`|Yerel demo olayını Kafka'ya gönderir (`prod` dışında ve demo açıkken)|
|`GET`|`/api/v1/members/{memberId}/tier`|Güncel statü ve dönem toplamı|
|`GET`|`/api/v1/members/{memberId}/tier-history`|Upgrade, renewal ve downgrade geçmişi|
|`GET`|`/api/v1/members/{memberId}/mileage-ledger`|Pozitif/negatif statü mili hareketleri|
|`POST`|`/api/v1/admin/period-end`|Dönem sonu değerlendirmesini elle çalıştırır (`prod` dışında)|

Henüz hiç olayı bulunmayan bir üye sorgulanırsa `404 Member not found` dönmesi
beklenen davranıştır.

## 10\. Mimari özet

Akış iki Kafka aşamasına ayrılır:

1. `status-events` consumer'ı mesajı ham haliyle `raw\\\_event` tablosuna kaydeder.
2. Geçerli olayla aynı veritabanı transaction'ında outbox kaydı oluşturulur.
3. Outbox publisher mesajı `memberId` anahtarıyla `member-events` topic'ine taşır.
4. Tier processor aynı üyeye ait olayları partition sırasıyla işler; ledger,
dönem toplamı ve güncel statüyü günceller.
5. Statü değişikliği oluşursa aynı transaction'da ikinci bir outbox kaydı oluşur.
6. Outbox publisher `TIER\\\_CHANGED` olayını `tier-changed` topic'ine yayınlar.

```text
Kaynaklar -> status-events -> Ingestion -> raw\\\_event + outbox
                                      |
                                      v
                              member-events (memberId key)
                                      |
                                      v
                               Tier Processor
                         reference\\\_data + ledger + tier
                                      |
                                      v
                                  tier-changed
```

Kafka'nın at-least-once teslimatı kabul edilir. Benzersiz iş anahtarları,
idempotent consumer ve transactional outbox birlikte iş seviyesinde
exactly-once etki sağlar.

Ayrıntılar:

* [Mimari](docs/ARCHITECTURE.md)
* [Varsayımlar ve ürün kararları](docs/ASSUMPTIONS.md)
* [Vaka maddeleri kontrol listesi](docs/CASE_CHECKLIST.md)
* [Teslim notları](docs/DELIVERY_NOTES.md)

## 11\. Örnek kurallar

### Statü eşikleri

|Statü|İlk kazanım eşiği|Yenileme eşiği|
|-|-:|-:|
|Classic|0|0|
|Classic Plus|25.000|15.000|
|Elite|40.000|30.000|
|Elite Plus|80.000|60.000|

### Uçuş oranları

|İşleten / rezervasyon sınıfı|Oran|
|-|-:|
|TK/J|`%150`|
|TK/Y|`%100`|
|TK/P|`%50`|
|TK/X|`%0`|
|LH/C|`%150`|

Uçuş statü mili `distanceMiles × oran` şeklinde hesaplanır ve `HALF\\\_UP`
yuvarlanır. TK tarafından işletilen pozitif kazanımlı uçuşlarda sonuç 500'den
azsa minimum 500 mil uygulanır. Partner havayolunda bu minimum uygulanmaz.

## 12\. Testleri çalıştırma

En kolay doğrulama, Java veya Maven kurmadan Docker build'inin başarılı olması
ve ana Swagger demosunun çalışmasıdır.

Tüm otomatik testleri çalıştırmak için yerel JDK 21, Maven ve çalışan Docker
gerekir:

```bash
mvn clean verify
```

Testcontainers entegrasyon testleri PostgreSQL ve Kafka container'ları açar.
Docker kapalıysa bu testler `Skipped` olabilir; bu durum başarılı test olarak
yorumlanmamalıdır. Teslim öncesinde özetin `Failures: 0`, `Errors: 0` ve
`Skipped: 0` olduğu doğrulanmalıdır.

Test paketi başlıca şunları kapsar:

* Statü eşikleri, upgrade, renewal ve dönem sonu downgrade
* TK minimum 500 mil ve partner havayolu farkı
* `HALF\\\_UP` yuvarlama ve negatif manuel düzeltme
* Mükerrer teslimatın tek iş etkisi yaratması
* Outbox sırası
* Erken gelen reversal ve aktif dönem iptali
* Geçersiz mesaj doğrulaması

## 13\. Sorun giderme

### `port is already allocated` / `Address already in use`

Belirtilen portu başka süreç veya container kullanıyordur. Özellikle IntelliJ
uygulaması ile Docker `app` servisini aynı anda çalıştırmayın:

```bash
docker compose stop app
```

### IntelliJ'de PostgreSQL `Connection refused: localhost:5432`

Altyapıyı başlatın ve healthy olmasını bekleyin:

```bash
docker compose up -d postgres kafka
docker compose ps
```

### Swagger açılıyor fakat demo servisi görünmüyor

* Docker ile: `docker-compose.yml` içinde `DEMO\\\_ENABLED: "true"` olmalıdır.
* IntelliJ ile: Program arguments alanında `--tier.demo.enabled=true`
bulunmalıdır.

### Olay `202` döndü fakat tier hemen görünmüyor

İşleme Kafka üzerinden asenkron yürür. 1–3 saniye bekleyip sorguyu tekrarlayın.
Devam ederse uygulama loglarını kontrol edin:

```bash
docker compose logs --tail=200 app
```

### Aynı demo yeniden çalıştırıldığında toplam artmıyor

Bu beklenen idempotency davranışıdır; aynı `eventId` ikinci kez işlenmez. Yeni
`eventId` kullanın veya temiz başlangıç için `docker compose down -v` uygulayın.

## 14\. Teknoloji ve üretim notları

* Java 21 ve Spring Boot 3.5
* Spring Kafka ve Spring Data JPA
* PostgreSQL 16 ve Flyway
* Testcontainers
* OpenAPI / Swagger UI
* Docker Compose

`reference\\\_data`, prototipte aynı PostgreSQL içinde ayrı bir şema olarak
simüle edilir. Üretimde ayrı datasource veya servis adaptörü kullanılabilir.
OpenShift örnekleri API ile consumer yükünü ayrı deployment'lara böler.
Consumer ölçeklemesinde CPU yanında consumer lag, oldest-event-age, outbox
backlog ve DB pool metrikleri izlenmelidir.

Gerçek parola, token veya kurum içi bağlantı bilgileri repoya eklenmemelidir.
`deploy/openshift/secret.example.yaml` yalnız şablondur.

## 15\. Bilinçli kapsam sınırları

* Aktif dönemde iptal edilen orijinal işlem, desteklemediği upgrade'i geri
alabilir. Negatif manuel adjustment ise kendiliğinden anlık downgrade üretmez.
* Kapanmış döneme ait negatif olay ledger ve dönem toplamını düzeltir; geçmişte
kullanılmış faydayı ve tamamlanmış statü kararını otomatik geri almaz.
* Pozitif geç olayın kazandırdığı statü hâlâ geçerliyse statü hemen yansır.
* Outbox prototipte ilişkisel kilitle claim edilir; yüksek ölçekte CDC veya
veritabanına özel `SKIP LOCKED` optimizasyonu değerlendirilebilir.
* REST liste endpoint'leri en fazla 200 kayıt döndürür; üretimde cursor
pagination eklenmelidir.

