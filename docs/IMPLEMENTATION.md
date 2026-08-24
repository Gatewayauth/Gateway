# Implementation Guide

How to stand up the full Gateway stack — the Kotlin backend and the
[Keychain](../../Keychain) frontend — from a clean checkout to a working login,
external (Discord) sign-in, and an SSO-connected service.

- [1. Architecture](#1-architecture)
- [2. Prerequisites](#2-prerequisites)
- [3. Run the backend](#3-run-the-backend)
- [4. Run the frontend](#4-run-the-frontend)
- [5. Wire the two together](#5-wire-the-two-together)
- [6. Enable Discord login](#6-enable-discord-login)
- [7. Connect a service over SSO](#7-connect-a-service-over-sso)
- [8. Going to production](#8-going-to-production)
- [9. Troubleshooting](#9-troubleshooting)

---

## 1. Architecture

```
Browser ──▶ Keychain (Nuxt SPA, :3000) ──▶ Gateway backend (Ktor, :8080) ──▶ DB
                                                    ▲
        Google / GitHub / Discord ──────────────────┘   (external login)
        Grafana / YouTrack / … ──────────────────────▶  (OIDC relying parties)
```

- **Backend** owns all state: accounts, sessions, keys, OIDC. Cookie-authenticated
  JSON API. Never exposes tokens to the browser.
- **Frontend** is a client-rendered SPA. It only renders UI and calls the API with
  the session cookie; it stores nothing sensitive.
- **Session** is an HttpOnly `gw_session` cookie. Because it is a cookie, the two
  services must be **same-site** (both on `localhost`, or both under one parent
  domain like `example.com`).

---

## 2. Prerequisites

| Tool | Version | For |
|------|---------|-----|
| JDK | 21 | Gradle daemon (auto-provisioned by foojay) |
| Node | 20+ | Frontend |
| Yarn | 4 (via `corepack enable`) | Frontend |
| Docker | recent | Optional — Postgres / full deploy |

The default backend uses an in-memory H2 database, so no database install is needed
to get started.

---

## 3. Run the backend

From `Backend/`:

```bash
GATEWAY_COOKIE_SECURE=false \
GATEWAY_CORS_ORIGINS=http://localhost:3000 \
GATEWAY_ADMIN_TOKEN=dev-admin-token \
./gradlew :app:run
```

Why these:

- `GATEWAY_COOKIE_SECURE=false` — **required for local HTTP**. The session cookie
  defaults to `Secure`, which browsers refuse to set over plain `http://`. Without
  this you will "log in" but stay logged out.
- `GATEWAY_CORS_ORIGINS` — lets the frontend origin call the API with credentials.
- `GATEWAY_ADMIN_TOKEN` — needed later to register SSO clients. Skip if you don't
  need the admin API yet.

Confirm it's up:

```bash
curl http://localhost:8080/healthz
```

---

## 4. Run the frontend

From `Keychain/`:

```bash
corepack enable
yarn install
NUXT_PUBLIC_API_BASE=http://localhost:8080 yarn dev
```

Open `http://localhost:3000`. Register an account, then sign in — you should land on
`/account`.

---

## 5. Wire the two together

The three settings that must agree:

| Concern | Backend | Frontend |
|---------|---------|----------|
| API location | runs on `:8080` | `NUXT_PUBLIC_API_BASE=http://localhost:8080` |
| CORS | `GATEWAY_CORS_ORIGINS=http://localhost:3000` | served on `:3000` |
| Cookie | `GATEWAY_COOKIE_SECURE=false` for local HTTP | — |

The frontend's API client sends `credentials: 'include'` on every request, so the
session cookie rides along automatically once these line up.

---

## 6. Enable Discord login

1. In the [Discord Developer Portal](https://discord.com/developers/applications),
   create an application and open **OAuth2**.
2. Add this **redirect URL** (must match `GATEWAY_ISSUER` exactly):

   ```
   http://localhost:8080/t/default/api/auth/external/discord/callback
   ```

3. Copy the **Client ID** and **Client Secret**.
4. Restart the backend with them set:

   ```bash
   GATEWAY_COOKIE_SECURE=false \
   GATEWAY_CORS_ORIGINS=http://localhost:3000 \
   GATEWAY_DISCORD_CLIENT_ID=xxxxxxxx \
   GATEWAY_DISCORD_CLIENT_SECRET=yyyyyyyy \
   ./gradlew :app:run
   ```

The provider activates only when both id and secret are set. Google and GitHub work
the same way with `GATEWAY_GOOGLE_*` / `GATEWAY_GITHUB_*`.

**Where the browser lands afterward.** On success the backend redirects to
`GATEWAY_EXTERNAL_POST_LOGIN_REDIRECT`. If unset it defaults to the **first CORS
origin** (your frontend, `http://localhost:3000`) — so external login returns the
user to the UI, not to the backend. Override it only if your UI lives elsewhere:

```bash
GATEWAY_EXTERNAL_POST_LOGIN_REDIRECT=http://localhost:3000/account
```

Account linking is takeover-safe: an external identity links to an existing local
account only when the provider reports the email as verified; otherwise the callback
refuses.

---

## 7. Connect a service over SSO

Any OIDC relying party (Grafana, YouTrack, TeamCity, Seafile, …) connects the same
way. Example with Grafana.

Register the client (needs `GATEWAY_ADMIN_TOKEN`):

```bash
curl -X POST http://localhost:8080/t/default/api/admin/clients \
  -H "X-Admin-Token: dev-admin-token" \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "Grafana",
    "redirect_uris": ["https://grafana.example/login/generic_oauth"],
    "scopes": ["openid", "profile", "email"],
    "public": false
  }'
```

The response returns `client_id` and, for confidential clients, a one-time
`client_secret` — **copy it now**, it is not shown again.

Point the service at these endpoints:

All endpoints are tenant-scoped under `/t/{slug}` (`default` is seeded), including a
per-tenant issuer/discovery/JWKS. See "Multi-tenancy" in the backend README.

| Setting | Value |
|---------|-------|
| Issuer / discovery | `http://localhost:8080/t/default/.well-known/openid-configuration` |
| Authorization URL | `http://localhost:8080/t/default/oauth2/authorize` |
| Token URL | `http://localhost:8080/t/default/oauth2/token` |
| Userinfo URL | `http://localhost:8080/t/default/oauth2/userinfo` |

The login handshake: `/t/{slug}/oauth2/authorize` requires a Gateway session. With none
it returns `401 login_required`, which the frontend uses to drive login and retry. A
client that needs consent gets `200 consent_required`; the frontend renders the
consent screen and POSTs `/t/{slug}/oauth2/consent`. Public clients must use PKCE (S256).

---

## 8. Going to production

Switch from local defaults:

- **HTTPS + real domains.** Put both services under one registrable domain (e.g.
  `auth.example.com` and `app.example.com`) so the session cookie stays same-site.
  Drop `GATEWAY_COOKIE_SECURE=false` (let it default back to `Secure`).
- **Issuer.** Set `GATEWAY_ISSUER=https://auth.example.com`. Update every provider
  redirect URL and relying-party config to match.
- **CORS.** `GATEWAY_CORS_ORIGINS=https://app.example.com`.
- **Database.** Point at Postgres:

  ```bash
  docker compose up --build   # Postgres + Gateway
  ```

  or set `GATEWAY_DB_URL`, `GATEWAY_DB_USER`, `GATEWAY_DB_PASSWORD`.
- **Secrets.** Set a strong `GATEWAY_ENC_KEY` (rotating it invalidates stored TOTP
  secrets) and a real `GATEWAY_ADMIN_TOKEN`. Never commit them.
- **Email.** Set `GATEWAY_SMTP_HOST` (and friends) so verification and reset emails
  are actually sent instead of logged.
- **Key rotation.** Optionally set `GATEWAY_KEY_ROTATION_DAYS` to rotate signing keys
  on a schedule; retired keys remain in JWKS so existing tokens keep verifying.
- **Frontend.** Build (`yarn build`) and serve the SPA with
  `NUXT_PUBLIC_API_BASE=https://auth.example.com`.

---

## 9. Troubleshooting

| Symptom | Likely cause |
|---------|--------------|
| Log in succeeds but you're immediately logged out | `GATEWAY_COOKIE_SECURE` is `true` over plain HTTP — set it `false` for local dev |
| Browser console shows CORS errors | Frontend origin missing from `GATEWAY_CORS_ORIGINS` |
| Discord login ends on the backend, not the UI | Upgrade past the fix, or set `GATEWAY_EXTERNAL_POST_LOGIN_REDIRECT` to your UI |
| Discord returns `redirect_uri` mismatch | Portal redirect URL doesn't match `{GATEWAY_ISSUER}/t/{slug}/api/auth/external/discord/callback` exactly |
| SSO client rejected at `/t/{slug}/oauth2/authorize` | `redirect_uris` registered for the client don't include the one the service sent |
| Admin API returns 401/404 | `GATEWAY_ADMIN_TOKEN` unset (disables the admin API) or wrong `X-Admin-Token` |

Full API reference: run the backend and open `http://localhost:8080/swagger`.
