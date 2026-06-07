# Notion DB Widgets — Design & Plan

An Android app that displays Notion databases as interactive home-screen
widgets — a more feature-complete take on tools like notizenwidget.com, with
real write-back (checkboxes / Status) and configurable action buttons.

## Scope

Personal / hobby-first. Built to be genuinely useful for one person quickly,
but architected so it can grow into a distributable Play Store app without a
rewrite.

## Tech stack

| Concern | Choice |
| --- | --- |
| Language | Kotlin |
| Config / "builder" UI | Jetpack Compose + Material 3 |
| Home-screen widgets | **Jetpack Glance** |
| Local cache | Room |
| Refresh & write-back | WorkManager (scheduled + expedited) |
| Notion HTTP client | Ktor (OkHttp engine) + kotlinx.serialization |
| Notion API version | **2026-04-01** (Views API + smart filters) |
| Auth (v1) | User-pasted internal-integration token — no backend |
| Auth (later) | Notion OAuth via a Cloudflare Worker (holds client secret) |
| DI | Hand-rolled `AppContainer` (Hilt if it grows) |

## Two Notion API realities the design is built around

These come from the public API's shape and drove several decisions:

1. **Views are reusable (good news).** As of API version `2026-04-01`, Notion
   exposes a Views API: list a database's views, retrieve a view, **read its
   saved filters and sorts**, and query through it. It also added the `me`
   filter and relative-date filters (today / this week / …) that used to be
   UI-only. So the builder can let the user **pick an existing Notion view**
   and the widget mirrors it — instead of forcing them to rebuild filters by
   hand. This is why we pin the API version rather than float it.

2. **Buttons cannot be triggered (design around it).** The API has no endpoint
   to "press" a Button property, and a button's configured actions are not
   readable from the schema. So widget buttons are **re-declared in our
   builder** as app-defined actions (set a property, advance a Status, add a
   row, open the page, or fire a webhook). The *Send webhook* action is the
   closest 1:1 to a native Notion button and is the recommended bridge for
   automations (Zapier / Make / n8n).

General principle: **data-layer** things (rows, properties, schema, and now
views/filters) are accessible; the one remaining **view-layer** gap is the
Button property's actions, which we reproduce ourselves.

## Operational constraints

- **Rate limit:** ~3 requests/sec per integration, with HTTP 429 + `Retry-After`.
  All Notion calls go through one rate limiter; refresh is batched.
- **Widget refresh:** WorkManager periodic floor is ~15 min; tap/interaction
  triggers an *expedited* one-off refresh. Honest UX: "updates roughly every
  15–30 min, and instantly when you touch it." This also protects battery.
- **Widget surface:** Glance renders in the launcher process — list, text,
  image, checkbox, button only; interactions are fire-and-forget `Action`s that
  hand off to a Worker rather than running network inline.
- **Token at rest:** Phase 0 stores the token in plain DataStore. Encrypt with
  an Android Keystore key before any public release.

## Property-type coverage

- **First-class (render + edit):** Title, Text, Checkbox, Status, Select,
  Number, Date
- **Render-only (v1):** Multi-select, Person, URL / email / phone, Formula,
  Rollup, Created/Edited time
- **Later:** Relation (needs a second fetch), Files, complex Formula / Rollup

## Roadmap

Each phase is independently shippable.

- **Phase 0 — Foundations** ✅ *(scaffolded)*
  Gradle/Android project, Glance plumbing, `NotionAuthProvider` interface with
  a token-based implementation, token-entry screen that validates against
  `GET /v1/users/me`, Notion client pinned to `2026-04-01`.
- **Phase 1 — Read-only widget**
  Builder flow: pick database → pick an existing view (reuse its filters/sorts)
  → choose fields. Glance renders a vertical list of rows. Room cache +
  scheduled refresh.
- **Phase 2 — Write-back**
  Checkbox toggle and Status advance from the widget; optimistic UI + a
  WorkManager write-back queue with 429 backoff.
- **Phase 3 — Buttons / action engine**
  App-defined actions: set property, add row, open page, fire webhook.
- **Phase 4 — Builder polish**
  Theming, widget sizes/density, extra filters layered on a view, more property
  renderers.
- **Phase 5 — Distribution (optional)**
  Add `OAuthAuthProvider` + Cloudflare Worker, onboarding, Play Store.

## Module layout

Single Gradle module (`:app`) with clean packages — splittable later:

```
com.notiondb.widgets
├── auth/     NotionAuthProvider, TokenAuthProvider, TokenStore
├── data/     NotionClient (API 2026-04-01), models, NotionResult
├── di/        AppContainer (manual DI)
├── ui/        MainActivity, ConnectScreen, ConnectViewModel, theme/
└── widget/   NotionWidget (Glance), NotionWidgetReceiver
```

## Building

Requires the Android SDK; open in Android Studio (Ladybug or newer) and let it
sync, or build from the CLI with a configured SDK:

```
./gradlew :app:assembleDebug
```

Dependency versions live in `gradle/libs.versions.toml` and may need a nudge to
match your installed Android Studio / SDK.

## Connecting Notion (Phase 0)

1. Create an internal integration at <https://www.notion.so/my-integrations>.
2. Share the databases you want to widget-ify with that integration.
3. Launch the app and paste the integration token — it's verified live.
