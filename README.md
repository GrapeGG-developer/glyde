<p align="center"><img src="branding/glyde/app_icon_512.png" width="128" alt="Glyde"></p>

<h1 align="center">Glyde</h1>
<p align="center"><b>Обход DPI-блокировок и замедлений для Android. Одна кнопка, без серверов</b> · by GREENTUNE</p>
<p align="center">
<a href="https://github.com/GrapeGG-developer/glyde/releases/latest">Скачать APK</a> ·
<a href="PRIVACY.md">Конфиденциальность</a> ·
<a href="CHANGELOG.md">Что нового</a> ·
<a href="#english">English</a>
</p>

---

Glyde (раньше GT Unblock) — самостоятельное приложение с открытым кодом для обхода DPI-блокировок, аналог ByeByeDPI. Оно возвращает более 40 сервисов: Telegram, YouTube, Discord, WhatsApp, Instagram, X, Twitch, Signal, TikTok, GitHub, rutracker и другие. Подключается одной кнопкой, но **это не VPN**: серверов нет, трафик идёт напрямую к сайтам. Glyde меняет только форму пакетов, чтобы фильтр провайдера их не узнал.

## Возможности

- **Одна кнопка, один экран.** Большая стеклянная сфера и шторка снизу. Если потянуть шторку вверх, откроется всё остальное, а сфера уменьшится, но не закроется. Для обычного пользователя настраивать ничего не нужно.
- **Автоподбор и автопочинка.** Glyde сам проверяет, что заблокировано в вашей сети, подбирает стратегию для каждого сервиса и подбирает заново, если провайдер что-то поменял.
- **Ускорение Telegram.** Встроенный локальный прокси ведёт Telegram через WebSocket. Подключается одной кнопкой.
- **Звонки и игры.** Обход для UDP и QUIC помогает голосовым каналам Discord и звонкам.
- **Более 40 сервисов по категориям**, свои сайты и импорт хостлистов.
- **Проверка сети (аналог blockcheck)**: подмена DNS, блокировка по IP или DPI. Отчётом можно поделиться.
- **Обмен настройкой** через короткий код или QR-код и облачная база стратегий по оператору (ASN).
- **Экспертный режим** со всеми параметрами ByeByeDPI: конструктор команды, ручной выбор стратегий, свои параметры, журнал движка.
- **Выбор приложений**, режим «только прокси» (SOCKS5 с авторизацией), DNS через HTTPS, блокировка QUIC.
- **Мгновенные обновления** по постоянному соединению с сервером, зеркала на случай блокировки, поэтапная раскатка (Insider → Beta → Release) и проверка подлинности сборки.
- **Лёгкий режим** для слабых телефонов включается сам, плюс одна кнопка.
- **Aqua Aero в реальном времени.** Жидкая стеклянная сфера, каустика на GPU-шейдерах AGSL, живые пузыри с физикой (не слипаются, разлетаются от пальца, реагируют на наклон), глянцевые Aero-иконки, светлая и тёмная тема по системе.
- **Собственный шрифт Glyde Sans.** Геометричный шрифт с клиновидными засечками и волнами, латиница и кириллица (SIL OFL 1.1, исходники в `tools/font`).
- **Звуки-аккорды**, синтезированные в `tools/sound`.

## Версии

Версии называются `Glyde ГГ.ММ <Созвездие>`: Andromeda, Betelgeuse, Cassiopeia и дальше по алфавиту. Сначала сборку получает Insider, потом Beta, потом все, а исправления выходят как Patch. Первая версия — **Glyde 26.11 Andromeda**.

## Как это работает

```
Приложения → VpnService (TUN) → hev-socks5-tunnel → ciadpi (byedpi) → интернет
```

1. Android VpnService перехватывает трафик устройства на локальном TUN-интерфейсе. Root не нужен.
2. [hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel) превращает пакеты в соединения и передаёт их локальному SOCKS5.
3. [byedpi](https://github.com/hufrea/byedpi) (ciadpi) меняет начало соединений по выбранной стратегии: разбивает, переупорядочивает и подмешивает фейковые пакеты, чтобы DPI не распознал заблокированный адрес.
4. Для каждого сервиса используется своя стратегия, подобранная автоматически.

## Установка

1. Откройте [последний релиз](https://github.com/GrapeGG-developer/glyde/releases/latest).
2. Скачайте APK для своего процессора. Обычно подходит `arm64-v8a`. Если не уверены, берите `universal`.
3. Установите и разрешите VPN-подключение при первом включении.

Требуется Android 10 или новее.

## Сборка

```bash
git clone --recursive https://github.com/GrapeGG-developer/glyde.git
cd glyde
./gradlew assembleRelease
```

Нужны JDK 17, Android SDK 36 и NDK 27.2.12479018. Релизы собираются и подписываются в GitHub Actions по тегу `v*`.

## Конфиденциальность

Приложение всегда отправляет отчёты о сбоях и статистику: случайный ID устройства, IP-адрес (хранится 30 дней), страну и город, оператора и ASN, модель телефона, версии Android и Glyde, статус обхода и стратегию, какие из наших сервисов открываются. Историю сайтов и трафик Glyde не собирает. Подробнее в [PRIVACY.md](PRIVACY.md).

## Поддержать проект

Приложение бесплатное. Если оно вам помогает, можно [поддержать разработку](https://pay.cloudtips.ru/p/c9e9cb6d).

## Благодарности

- [byedpi](https://github.com/hufrea/byedpi) от hufrea (MIT)
- [hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel) от heiher (MIT)
- [ByeByeDPI](https://github.com/romanvht/ByeByeDPI) от romanvht (GPL-3.0), подсказавший архитектуру Android-части
- [zapret](https://github.com/bol-van/zapret) от bol-van (MIT), идеи стратегий
- [ZXing](https://github.com/zxing/zxing) (Apache-2.0), генерация QR-кодов

## Лицензия

[GPL-3.0](LICENSE). Сторонние компоненты распространяются под своими лицензиями, см. `app/src/main/jni/*/LICENSE`.

---

## English

**Glyde** (formerly GT Unblock) by GREENTUNE is an open-source DPI bypass app for Android, an alternative to ByeByeDPI. It brings back 40+ services (Telegram, YouTube, Discord, WhatsApp, Instagram, X, Twitch, Signal, TikTok, GitHub and more) with one tap. **It is not a VPN**: there is no server, traffic goes straight to websites and only the shape of packets changes.

Features: a single screen with a glass orb and a pull-up sheet, zero settings for regular users, automatic per-service tuning and auto repair, a Telegram WebSocket boost, UDP/QUIC bypass for calls and games, 40+ services and custom hostlists, a blockcheck-like network check, setup sharing via code or QR, ISP-aware cloud strategies, an expert mode with every ByeByeDPI option and a command builder, split tunneling, SOCKS5 mode with auth, DNS over HTTPS, instant updates over a persistent connection with mirrors and staged rollout (Insider, Beta, Release), build authenticity check, an automatic lite mode, a real-time Aqua Aero design with physics bubbles, the custom Glyde Sans typeface and chord sounds.

Versions are named `Glyde YY.MM <Constellation>`, starting with **Glyde 26.11 Andromeda**.

Privacy: crash reports and statistics are always sent (random device ID, IP kept for 30 days, country and city, ISP and ASN, device model, Android and Glyde versions, bypass status and strategy, which supported services open). Browsing history and traffic are never collected. See [PRIVACY.md](PRIVACY.md).

Licensed under GPL-3.0. The Glyde Sans font is licensed under SIL OFL 1.1.
