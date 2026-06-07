# Notion OAuth Worker

A tiny Cloudflare Worker that does the Notion OAuth **authorization-code → access-token** exchange so the Android app never has to embed the integration's `client_secret`. It is stateless — no user data is stored.

## Why it exists

Notion's OAuth token endpoint requires HTTP Basic auth with `client_id:client_secret`. Shipping the secret in an APK would leak it, so the app sends only the short-lived `code` to this Worker, which adds the secret and returns the token. The app then stores the token on-device.

## Setup

1. Create a **public** integration at <https://www.notion.com/my-integrations> and note its OAuth **Client ID** and **Client secret**.
2. Add the app's redirect URI to the integration: `notiondbwidgets://oauth`.
3. Deploy the Worker:
   ```bash
   cd worker
   npm install
   npx wrangler secret put NOTION_CLIENT_ID
   npx wrangler secret put NOTION_CLIENT_SECRET
   npm run deploy
   ```
4. Put the deployed URL and Client ID into the app at
   `app/src/main/java/com/notiondb/widgets/auth/NotionOAuthConfig.kt`.

## API

### `POST /oauth/token`
Request body:
```json
{ "code": "<authorization_code>", "redirect_uri": "notiondbwidgets://oauth" }
```
Response: Notion's token payload verbatim (includes `access_token`, `workspace_name`, …), with Notion's HTTP status.

### `GET /health`
Returns `{ "ok": true }`.

## Local dev

```bash
cp .dev.vars.example .dev.vars   # fill in real values
npm run dev
```

## Note on refresh

Notion access tokens for public integrations are long-lived and there is no refresh-token flow today, so the Worker only implements the initial exchange. If Notion introduces rotation, add a `/oauth/refresh` route here and a matching call in the app's auth provider.
