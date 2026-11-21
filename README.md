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

Glyde (раньше GT Unblock) — самостоятельное приложение с открытым кодом для обхода DPI-блокировок. Оно возвращает 120 сервисов: Telegram, YouTube, Discord, WhatsApp, Instagram, X, Twitch, Signal, TikTok, GitHub, rutracker и другие. Подключается одной кнопкой, но **это не VPN**: серверов нет, трафик идёт напрямую к сайтам. Glyde меняет только форму пакетов, чтобы фильтр провайдера их не узнал.

## Возможности

- **Одна кнопка.** Нажали — заработало. Glyde сам проверяет сеть, подбирает обход для каждого сервиса и чинит его, если провайдер что-то поменял.
- **272 стратегии и 120 сервисов**: мессенджеры, соцсети, видео, музыка, игры, новости, файлы. Свои сайты и импорт хостлистов.
- **Telegram через WebSocket.** Встроенный локальный прокси работает даже при выключенном обходе.
- **Звонки и игры.** Обход для UDP и QUIC.
- **Проверка сети**: подмена DNS, блокировка по IP или DPI, отчёт одной ссылкой.
- **Облачная база стратегий** по оператору (ASN) и обмен настройкой через код или QR.
- **Экспертный режим**: все параметры движка, конструктор команды, журнал.
- **Выбор приложений**, SOCKS5 с авторизацией, DNS через HTTPS, блокировка QUIC.
- **Обновления за секунды** по постоянному соединению, зеркала, поэтапная раскатка и проверка подлинности сборки.
- **Лёгкий режим** одной кнопкой для слабых телефонов.
- **Дизайн [Air](https://github.com/GrapeGG-developer/air)**: стекло Aero, живой фон, который реагирует на подключение и скорость, наклон телефона, системная светлая и тёмная тема, шрифт Glyde Sans.

## Версии

Формат: `ГГ.ММ.Н.С Имя`, например **26.11.2.2 Astra**: год, месяц, неделя месяца и номер сборки за неделю. Каждую неделю — новое космическое имя и значок. Сначала сборку получает Insider, затем Beta, затем все.

## Как это работает

```
Приложения → VpnService (TUN) → hev-socks5-tunnel → ciadpi (byedpi) → интернет
```

1. VpnService перехватывает трафик на локальном TUN-интерфейсе. Root не нужен.
2. [hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel) превращает пакеты в соединения и передаёт их локальному SOCKS5.
3. [byedpi](https://github.com/hufrea/byedpi) меняет начало соединений: разбивает, переупорядочивает и подмешивает фейковые пакеты, чтобы DPI не узнал адрес.
4. Для каждого сервиса используется своя стратегия.

## Установка

1. Откройте [последний релиз](https://github.com/GrapeGG-developer/glyde/releases/latest).
2. Скачайте APK для своего процессора. Обычно подходит `arm64-v8a`, если не уверены — `universal`.
3. Установите и разрешите VPN-подключение при первом включении.

Нужен Android 10 или новее.

## Сборка

```bash
git clone --recursive https://github.com/GrapeGG-developer/glyde.git
cd glyde
./gradlew assembleRelease
```

JDK 17, Android SDK 36, NDK 27.2.12479018. Устройство проекта описано в [ARCHITECTURE.md](ARCHITECTURE.md), правила для изменений — в [CONTRIBUTING.md](CONTRIBUTING.md).

## Конфиденциальность

Приложение всегда отправляет отчёты о сбоях и статистику: случайный ID устройства, IP-адрес (хранится 30 дней), страну и город, оператора и ASN, модель телефона, версии Android и Glyde, статус обхода и стратегию, какие из наших сервисов открываются. Историю сайтов и трафик Glyde не собирает. Подробнее в [PRIVACY.md](PRIVACY.md).

## Поддержать проект

Приложение бесплатное. Если оно помогает, можно [поддержать разработку](https://pay.cloudtips.ru/p/c9e9cb6d).

## Благодарности

- [byedpi](https://github.com/hufrea/byedpi) от hufrea (MIT)
- [hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel) от heiher (MIT)
- [ZXing](https://github.com/zxing/zxing) (Apache-2.0)

## Лицензия

[GPL-3.0](LICENSE). Сторонние компоненты распространяются под своими лицензиями, см. `core/native/src/main/jni/*/LICENSE`. Шрифт Glyde Sans — SIL OFL 1.1.

---

## English

**Glyde** by GREENTUNE is an open-source DPI bypass app for Android. One tap brings back 120 services: Telegram, YouTube, Discord, WhatsApp, Instagram, X, Twitch, Signal, TikTok, GitHub and more. **It is not a VPN**: there is no server, traffic goes straight to websites and only the shape of packets changes.

Highlights: automatic per-service route planning with 272 strategies and auto repair, a Telegram WebSocket proxy, UDP/QUIC bypass, a network check, ISP-aware cloud strategies, an expert mode, split tunneling, SOCKS5, DNS over HTTPS, instant updates with mirrors and staged rollout, a lite mode and the [Air](https://github.com/GrapeGG-developer/air) design system.

Versions look like `26.11.2.2 Astra`: year, month, week of the month and build of the week, with a new cosmic name every week.

Privacy: crash reports and statistics are always sent (random device ID, IP kept for 30 days, country and city, ISP and ASN, device model, Android and Glyde versions, bypass status and strategy, which supported services open). Browsing history and traffic are never collected. See [PRIVACY.md](PRIVACY.md).

Build: `git clone --recursive`, then `./gradlew assembleRelease` (JDK 17, SDK 36, NDK 27.2). See [ARCHITECTURE.md](ARCHITECTURE.md) and [CONTRIBUTING.md](CONTRIBUTING.md).

Licensed under GPL-3.0. Glyde Sans is licensed under SIL OFL 1.1.
