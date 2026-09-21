# Teslim notları

Bu paket, vaka dokümanındaki dört zorunlu görevin temel akışlarını uygulayan bir prototiptir.
Trafik tepesine yakın gerçek zamanlı hizmet ve üretim dayanıklılığı yük testiyle doğrulanmamıştır.

## Teslim içeriği

- Spring Boot kaynak kodu ve Maven build tanımı
- Kafka tüketicileri, üyeye göre yeniden partition edilen iç topic ve transactional outbox
- PostgreSQL veri modeli ile Flyway migration
- Statü mili hesaplama, orijinal olaydan önce veya sonra gelen iptaller ve dönem sonu karar akışları
- Statü, statü geçmişi ve ledger REST servisleri
- Unit testler ile Kafka/PostgreSQL Testcontainers entegrasyon testi
- Kafka'ya örnek olay basan ve API sonucunu sorgulayan scriptler
- Docker Compose ile tek komutlu yerel kurulum
- OpenShift deployment, service, HPA ve config örnekleri
- Mimari, varsayımlar ve teknik mülakat sunumu

## Doğrulama sonucu

Önceki çalıştırmada unit testler başarılı, iki Testcontainers testi Docker'a erişilemediği
için atlanmıştır. Erken iptal, aktif dönem iptaliyle statünün geri alınması ve bozuk mesaj
senaryoları için entegrasyon testleri eklendi;
teslim öncesinde Docker çalışırken `mvn clean verify` ile `Skipped: 0` doğrulanmalıdır.

JSON örnekleri ile Docker Compose ve OpenShift YAML dosyaları ayrıca parse edilerek
sözdizimi açısından doğrulanmıştır.
