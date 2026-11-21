# Contributing

## Setup

```bash
git clone --recursive https://github.com/GrapeGG-developer/glyde.git
./gradlew assembleDebug
```

JDK 17, Android SDK 36, NDK 27.2.12479018.

## Adding a service

Add an entry to `app/src/main/assets/presets.json` and copy the file to `presets/presets_v3.json`, then bump `version`:

```json
{
  "id": "example",
  "name": "Example",
  "color": "#2196F3",
  "domains": ["example.com", "cdn.example.net"],
  "ipset": [],
  "tests": ["https://example.com/"],
  "cat": "social",
  "def": false
}
```

Services that block Russian users on their own side are not added.

## Adding a strategy

Append `{"id": "sNNN", "args": "..."}` to `strategies`. Arguments are passed to byedpi as is; test them on at least two providers before opening a pull request.

## Code style

- Kotlin official style, four spaces.
- No comments in code: names should explain themselves.
- New UI goes through `:air` components.
- New features go into their own module under `feature/` when they do not need `BuildConfig`.

## Pull requests

One topic per pull request. Describe what changed for the user and how you checked it.
