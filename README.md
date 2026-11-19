<p align="center"><img src="branding/app_icon_512.png" width="128" alt="GT Unblock"></p>

<h1 align="center">GT Unblock</h1>
<p align="center"><b>Обход DPI-блокировок для Android с живым Aqua 3D-дизайном</b> · by GREENTUNE</p>
<p align="center">
<a href="https://github.com/GrapeGG-developer/gt-unblock/releases/latest">Скачать APK</a> ·
<a href="PRIVACY.md">Конфиденциальность</a> ·
<a href="#english">English</a>
</p>

---

GT Unblock — самостоятельное приложение для обхода DPI-блокировок, аналог ByeByeDPI. Оно разблокирует более 40 сервисов (Telegram, YouTube, Discord, WhatsApp, Instagram, X, Twitch, Signal, TikTok, GitHub, rutracker и другие) прямо на телефоне. Подключается одной кнопкой, как VPN, но **сервера нет**: трафик никуда не уходит, меняется только то, как пакеты отправляются к сайту.

## Возможности

- **Одна кнопка.** Нажали на сферу, подождали, подключено. Есть плитка в шторке, виджет, автозапуск после перезагрузки и поддержка постоянного VPN (Always-on).
- **Более 40 сервисов по категориям.** Мессенджеры, соцсети, видео и стримы, игры, разработка, новости, торренты. Отмечаете только нужные, и подбор идёт быстрее.
- **Свои сайты и хостлисты.** Добавьте любой домен, вставьте список или импортируйте хостлист из файла (формат hosts и простой список доменов).
- **Автоподбор стратегий.** Приложение проверяет, что заблокировано в вашей сети, и подбирает свою стратегию для каждого сервиса. Подбор запускается при первом старте, при смене сети и по кнопке, а для одного сервиса можно подобрать отдельно долгим нажатием. Быстрый подбор занимает около 20 секунд, полный перебирает все пресеты.
- **Проверка сети (аналог blockcheck).** Для каждого сервиса показывает, что именно мешает: подмена DNS, блокировка по IP или DPI, и помогает ли текущая стратегия. Отчётом можно поделиться.
- **Тест скорости.** Скорость загрузки, пинг и джиттер прямо в приложении.
- **Обмен настройкой.** Подобранные стратегии и свои сайты упаковываются в короткий код или QR-код: другу с тем же оператором не нужно ждать подбора.
- **Учёт оператора.** Сеть и оператор определяются по ASN, а облачная база подсказывает, какие стратегии чаще срабатывают у вашего провайдера.
- **Обновляемые пресеты.** База стратегий встроена в APK и обновляется с сервера без выпуска новой версии.
- **Выбор приложений.** «Только выбранные» или «все, кроме выбранных».
- **Режим «только прокси».** Локальный SOCKS5 без VPN.
- **DNS через HTTPS.** Cloudflare, Google, Quad9 или AdGuard, чтобы оператор не подменял ответы DNS.
- **Блокировка QUIC** по умолчанию, чтобы приложения переходили на TCP, где работает обход.
- **Экспертный режим.** Ручной выбор стратегий, свои параметры ciadpi, импорт и экспорт, журнал движка.
- **Автообновления** со списком изменений и бета-каналом.
- **Aqua Aero 3D в реальном времени.** Стеклянная сфера с жидкостью, которая переливается от наклона телефона, подводный фон с каустикой и пузырьками на GPU-шейдерах AGSL (Android 13+, на старых версиях упрощённый вид), блики на стеклянных панелях следуют за наклоном, глянцевые Aqua-кнопки, светлая тема и Aqua Dark, выбор цвета.
- **Собственный шрифт Glasswave.** Округлый геометрический шрифт с латиницей и кириллицей, сгенерированный специально для GT Unblock (SIL OFL 1.1, исходники в `tools/font`).
- **Новые звуки.** Стеклянные синтезированные сигналы подключения, отключения, окончания подбора и ошибки (`tools/sound`).

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
- [ZXing](https://github.com/zxing/zxing) (Apache-2.0), генерация QR-кодов

## Лицензия

[GPL-3.0](LICENSE). Сторонние компоненты распространяются под своими лицензиями, см. `app/src/main/jni/*/LICENSE`.

---

## English

**GT Unblock** by GREENTUNE is a standalone DPI bypass app for Android, an alternative to ByeByeDPI. It unblocks 40+ services (Telegram, YouTube, Discord, WhatsApp, Instagram, X, Twitch, Signal, TikTok, GitHub and more) on the device itself. It connects with one tap like a VPN, but there is **no server**: traffic stays on your phone and only the way packets are sent changes.

Features: 40+ services in categories, your own sites and hostlist import, per-service automatic strategy tuning (quick ~20 s, full scan, single service), a blockcheck-like network check (DNS spoofing / IP block / DPI) with shareable reports, a speed test, setup sharing via code or QR, ISP detection by ASN with a cloud strategy base, updatable presets, split tunneling, SOCKS5-only mode, DNS over HTTPS, QUIC blocking, a hidden expert mode, auto-updates with a beta channel, a real-time Aqua Aero 3D design (AGSL shaders, tilt parallax), the custom Glasswave typeface and new synthesized sounds.

Architecture: `Apps → VpnService (TUN) → hev-socks5-tunnel → ciadpi (byedpi) → Internet`. Root is not required. Android 10+.

The app sends anonymous crash reports and minimal strategy statistics (app and Android version, device model, ISP ASN, which strategies worked). No IP addresses, device identifiers, browsing history or app lists are collected. See [PRIVACY.md](PRIVACY.md).

Licensed under GPL-3.0. The Glasswave font is licensed under SIL OFL 1.1.
