<p align="center"><img src="branding/app_icon_512.png" width="128" alt="GT Unblock"></p>

<h1 align="center">GT Unblock</h1>
<p align="center"><b>Неофициальный порт идей Zapret 2 для Android</b> · by GREENTUNE</p>
<p align="center">
<a href="https://github.com/GrapeGG-developer/gt-unblock/releases/latest">Скачать APK</a> ·
<a href="PRIVACY.md">Конфиденциальность</a> ·
<a href="#english">English</a>
</p>

---

GT Unblock обходит блокировки DPI (Telegram, YouTube, Discord, Instagram, X, rutracker и другие) прямо на телефоне. Подключается одной кнопкой, как VPN, но **сервера нет**: трафик никуда не уходит, меняется только то, как пакеты отправляются к сайту.

> GT Unblock не связан с авторами zapret, zapret2 и ZapretGUI. Это независимый проект с открытым кодом.

## Возможности

- **Одна кнопка.** Нажали на сферу, подождали, подключено. Есть плитка в шторке, виджет, автозапуск после перезагрузки и поддержка постоянного VPN (Always-on).
- **Автоподбор стратегий.** Приложение проверяет, что заблокировано в вашей сети, и подбирает свою стратегию для каждого сервиса. Подбор запускается при первом старте, при смене сети и по кнопке. Быстрый подбор занимает около 30 секунд, полный перебирает все пресеты.
- **Учёт оператора.** Сеть и оператор определяются по ASN, а облачная база подсказывает, какие стратегии чаще срабатывают у вашего провайдера.
- **Обновляемые пресеты.** База стратегий встроена в APK и обновляется с сервера без выпуска новой версии.
- **Выбор приложений.** «Только выбранные» или «все, кроме выбранных».
- **Режим «только прокси».** Локальный SOCKS5 без VPN.
- **DNS через HTTPS.** Cloudflare, Google, Quad9 или AdGuard, чтобы оператор не подменял ответы DNS.
- **Блокировка QUIC** по умолчанию, чтобы приложения переходили на TCP, где работает обход.
- **Экспертный режим.** Ручной выбор стратегий, свои параметры ciadpi, импорт и экспорт, журнал движка.
- **Автообновления** со списком изменений и бета-каналом.
- **Оформление в духе Windows 7 Aero.** Стекло с размытием, глянцевые кнопки, двойная обводка, зелёный прогресс-бар с бегущим бликом, светлая тема и Aero Dark, выбор цвета стекла.

## Как это работает

```
Приложения → VpnService (TUN) → hev-socks5-tunnel → ciadpi (byedpi) → интернет
```

1. Android VpnService перехватывает трафик устройства на локальном TUN-интерфейсе. Root не нужен.
2. [hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel) превращает пакеты в соединения и передаёт их локальному SOCKS5.
3. [byedpi](https://github.com/hufrea/byedpi) (ciadpi) меняет начало соединений по выбранной стратегии: разбивает, переупорядочивает и подмешивает фейковые пакеты, чтобы DPI не распознал заблокированный адрес.
4. Для каждого сервиса используется своя стратегия, подобранная автоматически.

## Установка

1. Откройте [последний релиз](https://github.com/GrapeGG-developer/gt-unblock/releases/latest).
2. Скачайте APK для своего процессора. Обычно подходит `arm64-v8a`. Если не уверены, берите `universal`.
3. Установите и разрешите VPN-подключение при первом включении.

Требуется Android 10 или новее.

## Сборка

```bash
git clone --recursive https://github.com/GrapeGG-developer/gt-unblock.git
cd gt-unblock
./gradlew assembleRelease
```

Нужны JDK 17, Android SDK 36 и NDK 27.2.12479018. Релизы собираются и подписываются в GitHub Actions по тегу `v*`.

## Конфиденциальность

Приложение отправляет анонимные отчёты о сбоях и минимальную статистику работы стратегий: версию приложения и Android, модель устройства, номер сети оператора (ASN) и какие стратегии сработали. IP-адрес, идентификаторы устройства, посещённые сайты и список приложений не собираются и не хранятся. Подробнее в [PRIVACY.md](PRIVACY.md).

## Поддержать проект

Приложение бесплатное. Если оно вам помогает, можно [поддержать разработку](https://pay.cloudtips.ru/p/c9e9cb6d).

## Благодарности

- [byedpi](https://github.com/hufrea/byedpi) от hufrea (MIT)
- [hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel) от heiher (MIT)
- [ByeByeDPI](https://github.com/romanvht/ByeByeDPI) от romanvht (GPL-3.0), подсказавший архитектуру Android-части
- [zapret](https://github.com/bol-van/zapret) от bol-van (MIT), идеи стратегий

## Лицензия

[GPL-3.0](LICENSE). Сторонние компоненты распространяются под своими лицензиями, см. `app/src/main/jni/*/LICENSE`.

---

## English

**GT Unblock** is an unofficial Android port of Zapret 2 ideas by GREENTUNE. It bypasses DPI-based blocking (Telegram, YouTube, Discord, Instagram, X and more) on the device itself. It connects with one tap like a VPN, but there is **no server**: traffic stays on your phone and only the way packets are sent changes.

Features: one-tap orb, quick settings tile, widget, autostart, Always-on VPN support, per-service automatic strategy tuning (quick ~30 s and full scan), ISP detection by ASN with a cloud strategy base, updatable presets, split tunneling, SOCKS5-only mode, DNS over HTTPS, QUIC blocking, a hidden expert mode, auto-updates with a beta channel, and a Windows 7 Aero inspired design.

Architecture: `Apps → VpnService (TUN) → hev-socks5-tunnel → ciadpi (byedpi) → Internet`. Root is not required. Android 10+.

The app sends anonymous crash reports and minimal strategy statistics (app and Android version, device model, ISP ASN, which strategies worked). No IP addresses, device identifiers, browsing history or app lists are collected. See [PRIVACY.md](PRIVACY.md).

GT Unblock is not affiliated with the authors of zapret, zapret2 or ZapretGUI. Licensed under GPL-3.0.
