/**
 * Notion OAuth token-exchange Worker.
 *
 * Holds the Notion integration's client_secret (never shipped in the APK) and
 * performs the authorization-code → access-token exchange on the app's behalf.
 * It stores nothing: the app keeps the returned token on-device. This is the
 * backend half of Phase 5; the app calls POST /oauth/token after the user
 * authorizes in the browser.
 */

export interface Env {
  NOTION_CLIENT_ID: string;
  NOTION_CLIENT_SECRET: string;
  // Optional: restrict who may call this Worker (CORS). Defaults to "*".
  ALLOWED_ORIGIN?: string;
}

const NOTION_TOKEN_URL = "https://api.notion.com/v1/oauth/token";
const NOTION_VERSION = "2026-04-01";

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);

    if (request.method === "OPTIONS") {
      return cors(new Response(null, { status: 204 }), env);
    }
    if (url.pathname === "/health") {
      return cors(json({ ok: true }), env);
    }
    if (url.pathname === "/oauth/token" && request.method === "POST") {
      return cors(await handleTokenExchange(request, env), env);
    }
    return cors(json({ error: "not_found" }, 404), env);
  },
};

async function handleTokenExchange(request: Request, env: Env): Promise<Response> {
  let body: { code?: string; redirect_uri?: string };
  try {
    body = await request.json();
  } catch {
    return json({ error: "invalid_json" }, 400);
  }

  const { code, redirect_uri } = body;
  if (!code || !redirect_uri) {
    return json({ error: "missing_code_or_redirect_uri" }, 400);
  }

  // Notion expects HTTP Basic auth with client_id:client_secret.
  const basic = btoa(`${env.NOTION_CLIENT_ID}:${env.NOTION_CLIENT_SECRET}`);

  const notionResp = await fetch(NOTION_TOKEN_URL, {
    method: "POST",
    headers: {
      Authorization: `Basic ${basic}`,
      "Content-Type": "application/json",
      "Notion-Version": NOTION_VERSION,
    },
    body: JSON.stringify({
      grant_type: "authorization_code",
      code,
      redirect_uri,
    }),
  });

  const data = await notionResp.json();
  // Pass Notion's response (and status) straight back to the app.
  return json(data, notionResp.status);
}

function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function cors(resp: Response, env: Env): Response {
  const headers = new Headers(resp.headers);
  headers.set("Access-Control-Allow-Origin", env.ALLOWED_ORIGIN ?? "*");
  headers.set("Access-Control-Allow-Methods", "POST, OPTIONS");
  headers.set("Access-Control-Allow-Headers", "Content-Type");
  return new Response(resp.body, { status: resp.status, headers });
}
