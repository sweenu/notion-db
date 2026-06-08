# CLAUDE.md

Guidance for Claude Code (and humans) working in this repo.

## What this is

An Android app showing Notion databases as home-screen widgets. Kotlin, Jetpack
Compose (builder UI), **Jetpack Glance** (widgets), Room (cache), WorkManager
(refresh + write-back), Ktor + kotlinx.serialization (Notion REST client).

This project is vibe-coded with Claude — keep changes small and verified.

## Environment & commands (NixOS + devenv)

The Android toolchain (SDK 35, emulator, JDK 17) is provided by `devenv.nix`.
Run everything inside the env:

```sh
devenv shell -- ./gradlew :app:assembleDebug          # build debug APK
devenv shell -- ./gradlew :app:testDebugUnitTest      # unit tests (JVM + Robolectric)
devenv shell -- ./gradlew :app:installDebug           # install to a running device/emulator
```

Driving the headless emulator (what Claude uses to verify):

```sh
emulator -avd nd_test -no-window -no-audio -no-snapshot -gpu swiftshader_indirect &
adb wait-for-device
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb exec-out screencap -p > /tmp/s.png          # screenshot (then read it)
adb exec-out run-as com.notiondb.widgets cat databases/notion_widgets.db > /tmp/nd.db  # inspect the Room cache (debug build)
```

devenv puts AVDs under `<repo>/.android` (`ANDROID_AVD_HOME`); the debug keystore
lives there too, so build with the same env or reinstalls fail with a signature
mismatch.

`notion_token` (gitignored) holds a real integration token used for live API
checks during development. Never commit it.

## Architecture

Single `:app` module, packages under `com.notiondb.widgets`:

- `auth/` — `NotionAuthProvider` (token impl now; OAuth later), `TokenStore`
- `data/` — `NotionClient` (REST), `NotionJson` (all wire-format parsing),
  Room (`data/local`), `WidgetRepository` (fetch → cache orchestration)
- `model/` — `WidgetConfig`, `WidgetRow`, `WidgetTheme`, `ButtonAction`
- `ui/` — `MainActivity` (connect), `WidgetConfigActivity` + `ui/builder` (the builder)
- `widget/` — `NotionWidget` (Glance) + receiver, row UI, action callbacks
- `work/` — `RefreshWorker`, `WriteBackWorker`, `ButtonActionWorker`, schedulers

The launcher process renders the widget purely from the Room cache; all network
happens in workers / action callbacks.

## Notion API specifics (learned the hard way — don't regress these)

- **API version is `2026-03-11`** (`NotionClient.NOTION_VERSION`). `2026-04-01`
  is NOT valid and is rejected on every request. Don't "upgrade" it blindly.
- **Data sources (2025-09-03+):** databases contain data sources. Search uses
  `filter.value = "data_source"` (not `"database"`), and rows are queried at
  `POST /v1/data_sources/{id}/query`.
- **View filters are NOT in the view's `filter` field** for UI-created filters —
  that field is `null`. The conditions live in `quick_filters` (and advanced
  filters in `filter`). **Do not try to translate them into a query filter**:
  Status conditions reference *groups* (e.g. "Complete") that the query filter
  rejects, relative dates and and/or trees compound the problem, and it fails
  *silently* (returns everything). Instead, **query the view**:
  `POST /v1/views/{id}/queries` → page through ids via
  `GET …/queries/{query_id}` → `GET /v1/pages/{id}` for each. Notion applies the
  real filter/sort; cost is bounded by rows displayed (≤ maxRows). This is
  `NotionClient.queryView`.
- **Views API**: `GET /v1/views?database_id=…` returns only `{object,id}`;
  retrieve each with `GET /v1/views/{id}` for name/type. There is no
  `/views/{id}/filters` endpoint, and the (singular) `/views/{id}/query` does
  not exist — it's the plural async `/queries` flow above.
- **Status as a checkbox**: "checked" = the topmost option of the *Complete*
  group, where "top" = order in the property's `options` list (NOT the group's
  `option_ids` order — those can differ). See `StatusCheckbox`.

## Glance gotchas

- **Do not give `LazyColumn` items a stable `itemId`** — the RemoteViews
  collection caches item views by id and won't re-render when only content
  (e.g. `checked`) changes, so toggles appear to revert. Let it key by position.
- The interactive `CheckBox` compound button keeps its own toggle state and
  fights optimistic updates; we render a **data-driven ☑/☐ glyph** instead.
- `update()`/`updateAll()` recompose from the cache; refresh inline in an action
  for immediacy (WorkManager scheduling is too laggy for a tap).

## Testing

`./gradlew :app:testDebugUnitTest` runs: JSON-parsing tests pinned to real API
shapes (`NotionJsonTest`), write-back JSON (`PropertyPatchTest`), the
`queryView` view-query flow via Ktor MockEngine (`NotionClientViewQueryTest`),
Status-checkbox resolution (`StatusCheckboxTest`), and a Glance render test
under Robolectric (`WidgetRenderTest`). CI runs these before building the APK.

When verifying widget behavior, prefer: drive the emulator, screenshot, and
inspect the Room cache (`cached_row` / `widget_config`) — that's the source of
truth the widget renders from.

## Conventions

- Commit/push only when asked; this repo uses the `claude/*` working branch.
- Keep comments meaningful (the why), not narration.
- Verify changes on the emulator (or via tests) before claiming they work.
