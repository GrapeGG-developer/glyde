# Политика конфиденциальности GT Unblock

_Последнее обновление: 17 ноября 2026 г._

GT Unblock разрабатывает GREENTUNE. Это приложение с открытым исходным кодом, поэтому всё, что описано ниже, можно проверить в репозитории.

## Как работает приложение

GT Unblock использует системный механизм Android VpnService только для того, чтобы обработать трафик **на самом устройстве**. Трафик не отправляется на серверы GREENTUNE или третьих лиц. Приложение не видит и не сохраняет содержимое ваших соединений.

## Что отправляется

Отчёты о сбоях и анонимная статистика включены по умолчанию. Отправляется только следующее:

| Данные | Зачем |
|---|---|
| Версия приложения и Android, модель и производитель устройства, архитектура процессора | Найти и исправить сбои на конкретных устройствах |
| Номер автономной системы оператора (ASN) и страна | Понять, какие стратегии работают у конкретного провайдера |
| Какие стратегии сработали для каких сервисов, время отклика | Подсказывать другим пользователям того же оператора рабочие стратегии |
| Режим работы (VPN или прокси), язык интерфейса, включены ли экспертный режим и блокировка QUIC, режим выбора приложений (все, только выбранные, кроме выбранных) | Понять, какими функциями пользуются |
| Стектрейс сбоя приложения | Исправить ошибку |
| Текст сообщения и контакт, если вы сами отправили предложение | Ответить вам и улучшить приложение |

## Что не собирается

- IP-адрес. Сервер не записывает его в базу данных.
- Идентификаторы устройства, рекламные идентификаторы, номер телефона, аккаунты.
- Посещённые сайты, DNS-запросы, содержимое трафика.
- Список установленных приложений. Выбор приложений для обхода хранится только на устройстве.
- Геолокация.

## Сторонние сервисы

- Для определения оператора приложение обращается к ipinfo.io или ipwho.is. Эти сервисы видят ваш IP-адрес по своим правилам.
- DNS-запросы отправляются выбранному вами DoH-провайдеру: Cloudflare, Google, Quad9 или AdGuard.
- «Проверка сети» обращается напрямую к проверяемым сервисам, а тест скорости загружает данные с speed.cloudflare.com. Результаты остаются на устройстве, пока вы сами не отправите отчёт.
- Обновления загружаются с GitHub или через сервер GT Unblock.
- Сервер статистики размещён на Render, база данных на Turso.

## Хранение

Статистика хранится в обезличенном виде и используется только для работы приложения и подбора стратегий. Данные не продаются и не передаются третьим лицам.

## Контакты

Вопросы можно задать через кнопку «Отправить предложение» в приложении или в [Issues на GitHub](https://github.com/GrapeGG-developer/gt-unblock/issues).

---

# GT Unblock Privacy Policy (English)

GT Unblock uses Android VpnService only to process traffic **on the device**. Traffic is never routed to GREENTUNE or third-party servers.

Crash reports and anonymous statistics are enabled by default. Only the following is sent: app and Android version, device model and maker, CPU ABI; ISP ASN and country; which strategies worked for which services and response times; mode, UI language, expert mode, QUIC blocking and app selection mode; crash stack traces; feedback text and contact if you send it yourself.

Not collected: IP addresses (not stored by the server), device or advertising identifiers, browsing history, DNS queries, traffic contents, installed app lists, location.

Third parties: ipinfo.io or ipwho.is for ISP detection, the DoH provider you select, GitHub for updates, Render and Turso for the statistics server. The network check connects directly to the services being checked, and the speed test downloads data from speed.cloudflare.com; results stay on the device unless you share a report.
