# Architecture

## Modules

| Module | Package | Purpose |
|---|---|---|
| `:app` | `gpr.greentune.unblock` | Screens, VPN service, engine orchestration, tuner, updater |
| `:air` | `gpr.greentune.air` | Air design system: theme, glass, orb, live background, controls, motion |
| `:core:common` | `gpr.greentune.unblock.core`, `.net` | Logging, HTTP client, network keys, local ports |
| `:core:data` | `gpr.greentune.unblock.data` | Settings and tuning results |
| `:core:native` | `gpr.greentune.unblock.core` | byedpi (`libciadpi.so`) and hev-socks5-tunnel, JNI bindings |
| `:feature:telegram` | `gpr.greentune.unblock.tg` | Local MTProto proxy over WebSocket |

```
:app ──► :air
  │──► :feature:telegram ──► :core:common
  │──► :core:native ───────► :core:common
  │──► :core:data
  └──► :core:common
```

Library modules never depend on `:app`. Anything that needs `BuildConfig` stays in `:app`.

## Connection path

```
apps → VpnService (TUN) → hev-socks5-tunnel → ciadpi → network
                                      └─────► TgBridge (Telegram DCs)
```

`Engine` starts `Ciadpi` with arguments built by `Args` from the active `PresetData`, then starts the TUN service. `Tuner` probes every enabled service, picks direct, bypass or proxy per service and stores the result in `TuneStore`. `Health` re-runs the tuner when a service stops opening.

## Presets

`app/src/main/assets/presets.json` ships with the APK. `presets/presets_v3.json` is served by the backend and wins when its `version` is higher. A service is a list of domains, optional IP ranges and test URLs; a strategy is a byedpi argument string. `{sni}` is replaced with `fakeSni`.

## Versions

`tools/version.py` turns a tag into everything the build needs:

```
python3 tools/version.py 26.11.3.1
name=26.11.3.1
version=11.3.1-Achtototam
year=2026
code=1261130001
codename=Achtototam
display=Glyde 11.3.1-Achtototam
```

Tags carry the year (`26.11.3.1`), the app shows `11.3.1-Achtototam`. `code = 1e9 + yy·1e7 + mm·1e5 + week·1e4 + build`, week is `day // 7`. Weekly names come from `NAMES` (the same list lives in `backend/services/releases.js`). The icon is generated from the name by `ui/brand/Cosmic.kt`; `tools/cosmic.py` renders the same icon to PNG.

## Backend

```
backend/
  server.js            entry, routing, limits
  lib/http.js          send, readJson, router
  lib/db.js            Turso client and migrations
  routes/updates.js    /v3/check, /v3/live, /v3/build, /v1/update, /v1/apk
  routes/strategies.js /v*/presets, /v1/best, /v3/services
  routes/telemetry.js  /v3/status, /v1/report, /v1/ping, /v1/crash, /v1/feedback
  services/releases.js GitHub releases, rings, version parsing
  services/live.js     SSE connections, devices, staged rollout
  services/jobs.js     retention and release watcher
  admin/               web panel
```

## Releases

Push a tag such as `26.11.2.2`. GitHub Actions builds and signs per-ABI APKs, publishes the release with `SHA256SUMS.txt` and refreshes the static update mirror on `gh-pages`. Clients learn about it over the live connection within seconds.
