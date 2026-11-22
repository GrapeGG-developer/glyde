# Политика конфиденциальности Glyde

_Последнее обновление: 19 ноября 2026 г._

Glyde разрабатывает GREENTUNE. У приложения открытый исходный код, поэтому всё, что описано ниже, можно проверить в репозитории.

## Как работает приложение

Glyde — не VPN в привычном смысле. Он использует системный механизм Android VpnService, чтобы обработать трафик **на самом устройстве**. Трафик не отправляется на серверы GREENTUNE или третьих лиц, а приложение не видит и не сохраняет содержимое ваших соединений. Ускорение Telegram работает как локальный прокси на 127.0.0.1 и соединяется напрямую с серверами Telegram.

## Что отправляется

Отчёты о сбоях и статистика отправляются всегда, отключить их нельзя. Отправляется только следующее:

| Данные | Зачем |
|---|---|
| Случайный ID устройства, созданный приложением (не связан с IMEI, рекламным ID или аккаунтами) | Поэтапная раскатка обновлений, канал Insider, защита сервера от злоупотреблений |
| IP-адрес (хранится 30 дней), страна и город по IP | Защита сервера от атак, статистика по регионам |
| Оператор и номер автономной системы (ASN) | Понять, какие стратегии работают у конкретного провайдера |
| Тип сети (Wi‑Fi, мобильная, Ethernet), состояние соединения и сетевой профиль без SSID | Диагностика разрывов и сопоставление результатов с типом подключения |
| Модель и производитель устройства, версия Android, архитектура процессора, объём памяти, версия Glyde, канал обновлений, включён ли лёгкий режим | Найти и исправить сбои на конкретных устройствах |
| Статус обхода (включён, выключен, ошибка), выбранная стратегия и результаты тестов доступности поддерживаемых сервисов | Видеть, что обход работает, и чинить неработающие профили |
| Какие из поддерживаемых Glyde сервисов открываются, а какие нет | Улучшать стратегии |
| Результат установки обновлений | Чинить проблемы с обновлением |
| Стектрейс сбоя приложения | Исправить ошибку |
| Текст сообщения и контакт, если вы сами отправили предложение | Ответить вам и улучшить приложение |

## Что не собирается

- История посещённых сайтов, DNS-запросы, содержимое трафика.
- Номер телефона, аккаунты, рекламные идентификаторы, IMEI.
- Список установленных приложений. Выбор приложений для обхода хранится только на устройстве.
- Точная геолокация по GPS; названия Wi‑Fi-сетей (SSID).

## Сторонние сервисы

- Для определения оператора приложение обращается к ipinfo.io или ipwho.is. Эти сервисы видят ваш IP-адрес по своим правилам.
- DNS-запросы отправляются выбранному DoH-провайдеру: Cloudflare, Google, Quad9 или AdGuard.
- «Проверка сети» обращается напрямую к проверяемым сервисам, а тест скорости загружает данные с speed.cloudflare.com.
- Обновления загружаются с GitHub, через сервер Glyde или его зеркала (GitHub Pages, jsDelivr).
- Сервер размещён на Render, база данных на Turso.

## Хранение

IP-адреса удаляются через 30 дней. Остальная статистика используется только для работы приложения и подбора стратегий. Данные не продаются и не передаются третьим лицам.

## Контакты

Вопросы можно задать через кнопку «Отправить предложение» в приложении или в [Issues на GitHub](https://github.com/GrapeGG-developer/glyde/issues).

---

# Glyde Privacy Policy (English)

Glyde uses Android VpnService only to process traffic **on the device**. Traffic is never routed to GREENTUNE or third-party servers. The Telegram boost is a local proxy on 127.0.0.1 that connects directly to Telegram.

Technical telemetry is sent automatically and cannot be turned off. It includes a random app-generated device ID; IP address (kept for up to 30 days), country/city; ISP and ASN; device model and maker, Android version, CPU ABI, RAM, Glyde version, update channel and lite mode; network transport type (not Wi-Fi SSID), connection state, selected strategy, supported-service test results, update install results and crash traces; feedback text/contact only when submitted by the user.

Not collected: browsing history, DNS queries, traffic contents, phone number, accounts, advertising IDs, IMEI, installed-app lists, precise GPS location or Wi-Fi SSIDs.

Third parties: ipinfo.io or ipwho.is for ISP detection, the DoH provider you select, GitHub, GitHub Pages and jsDelivr for updates, Render and Turso for the server.
