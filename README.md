# Notion DB Widgets

An Android app that puts your **Notion databases on your home screen** as
interactive widgets — render rows from a database or a saved view, tick
checkboxes / advance Status (with write-back to Notion), tap action buttons, and
open pages. A more feature-complete take on tools like notizenwidget.com.

> [!WARNING]
> **This project is "vibe coded" with Claude.** Essentially all of the code,
> architecture, tests, and even this README were written by Claude (Anthropic's
> AI) through conversation, with a human steering. Treat it accordingly: it has
> not had a traditional human code review, it may contain bugs or naïve choices,
> and the security posture has not been audited. Use at your own risk.

## What it does

- **Pick a database or a saved view** in an in-app builder, choose the title +
  fields to show, and place a widget.
- **Saved-view filters are honored exactly** — the app uses Notion's view-query
  endpoint, so a "Today, not completed" view shows precisely what Notion shows.
- **Interactive**: toggle a checkbox or a Status-as-checkbox, advance a Status,
  or tap app-defined buttons (open page, add row, webhook) — changes are written
  back to Notion.
- **Page emoji icons**, per-row Status, and a configurable list of fields.
- Refreshes on a schedule and on tap (header ↻); reconfigure via the header ⚙.

## Build & run

The toolchain is captured in a [devenv](https://devenv.sh) (Android SDK 35 +
emulator + JDK 17), so any machine gets the same setup:

```sh
devenv shell            # enters the env (adb, emulator, gradle, ANDROID_HOME…)
./gradlew :app:assembleDebug      # build the debug APK
./gradlew :app:testDebugUnitTest  # run the unit tests
```

You can also **download a ready-to-install APK** from CI: every push runs the
**Build APK** GitHub Action, which uploads `app-debug.apk` as an artifact.

### Connect Notion

1. Create an **internal integration** at <https://www.notion.com/my-integrations>.
2. **Share** the databases you want with that integration.
3. Launch the app and paste the integration token (it's verified live).

(A "Connect with Notion" OAuth flow exists too, but is dormant until you fill in
`NotionOAuthConfig` + deploy the Cloudflare Worker in `worker/`.)

## Tech stack

Kotlin · Jetpack Compose (builder UI) · **Jetpack Glance** (widgets) · Room
(cache) · WorkManager (refresh + write-back) · Ktor + kotlinx.serialization
(Notion client, API version `2026-03-11`).

See [`docs/PLAN.md`](docs/PLAN.md) for the design and `CLAUDE.md` for build/dev
notes and the hard-won Notion-API specifics.
