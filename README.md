# Gateway

A self-hosted **OpenID Connect / OAuth2 identity provider** and authentication portal —
a simpler, open-source alternative to Keycloak. Run your own SSO for services like
Grafana, YouTrack, TeamCity, Seafile, and SeaweedFS.

This repo is the **backend** (JSON API + OIDC endpoints), written in Kotlin on Ktor.
The user interface is a separate Nuxt frontend, [Keychain](../Keychain).

For a full walkthrough of running both services together, see
[docs/IMPLEMENTATION.md](docs/IMPLEMENTATION.md).

## Features

- **Local accounts** — registration, Argon2id password hashing, server-side sessions
- **External login** — Google, GitHub, Discord (OAuth2 + PKCE, takeover-safe linking)
- **MFA** — TOTP enrollment with encrypted secrets and one-time recovery codes
- **OIDC provider** — authorization-code + PKCE, ID/access JWTs (RS256), refresh-token
  rotation with reuse detection, discovery + JWKS
- **Rotatable signing keys** — persistent, encrypted at rest, on-demand or scheduled
  rotation; retired keys stay in JWKS so old tokens still verify
- **Email flows** — verification and password reset via SMTP (or a log-only dev mailer)
- **Admin API** — manage OAuth clients, users, sessions, keys, and the audit log
- **Audit log** — append-only record of security events
- **API docs** — Swagger UI at `/swagger`

Storage is Postgres or H2 via Exposed + Flyway migrations. CORS and per-IP rate
limiting on auth endpoints are built in.

> Roadmap: WebAuthn / passkeys.

## Requirements

- JDK 21 for the Gradle daemon (pinned in `gradle/gradle-daemon-jvm.properties`,
  auto-provisioned by foojay).

## Run locally

Zero-config — in-memory H2, migrations applied on startup:

```bash
./gradlew :app:run
```

Verify:

```bash
curl http://localhost:8080/healthz
curl http://localhost:8080/.well-known/openid-configuration
```

Register and sign in. All portal + OIDC endpoints are **tenant-scoped** under
`/t/{slug}`; a `default` tenant is seeded automatically, so use `/t/default/…`:

```bash
curl -X POST http://localhost:8080/t/default/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@example.com","password":"correcthorsebattery","displayName":"Admin"}'

curl -i -c cookies.txt -X POST http://localhost:8080/t/default/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@example.com","password":"correcthorsebattery"}'

curl -b cookies.txt http://localhost:8080/t/default/api/auth/me
```

The dev mailer logs verification/reset links to the app log instead of sending them.

## Build & test

```bash
./gradlew build                    # compile + test + detekt
./gradlew testCodeCoverageReport   # aggregated JaCoCo report
```

## Configuration

Configured via environment variables (see `app/src/main/resources/application.conf`
for the full list). The essentials:

| Variable | Purpose |
|----------|---------|
| `GATEWAY_ISSUER` | Public base URL; must match your OIDC relying-party config |
| `GATEWAY_CORS_ORIGINS` | Comma-separated frontend origins allowed to call the API |
| `GATEWAY_DB_URL` / `_USER` / `_PASSWORD` | Postgres connection (defaults to H2) |
| `GATEWAY_ADMIN_TOKEN` | Bootstrap token for the admin API (empty disables it) |
| `GATEWAY_ENC_KEY` | Passphrase for encrypting TOTP secrets and MFA tokens |
| `GATEWAY_REDIS_URL` | Optional `redis://host:port`. Set it to share rate-limit / MFA-lockout / key-rotation state across instances; unset uses in-memory (single instance) |
| `GATEWAY_EXTERNAL_POST_LOGIN_REDIRECT` | Where to land the browser after external login (defaults to the first CORS origin) |
| `GATEWAY_{GOOGLE,GITHUB,DISCORD}_CLIENT_ID` / `_SECRET` | Enable a provider |

## External login

A provider activates once its client id and secret are set. Register this callback
URL with each provider:

```
{GATEWAY_ISSUER}/t/{slug}/api/auth/external/{google|github|discord}/callback
```

## SSO / OIDC

Register a relying party (requires `GATEWAY_ADMIN_TOKEN`):

```bash
curl -X POST http://localhost:8080/t/default/api/admin/clients \
  -H "X-Admin-Token: $GATEWAY_ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Grafana","redirect_uris":["https://grafana.example/login/generic_oauth"],"scopes":["openid","profile","email"],"public":false}'
```

Point the service at (all endpoints are tenant-scoped — see Multi-tenancy):

| | Endpoint |
|---|---|
| Discovery | `/t/{slug}/.well-known/openid-configuration` |
| Authorization | `/t/{slug}/oauth2/authorize` |
| Token | `/t/{slug}/oauth2/token` |
| Userinfo | `/t/{slug}/oauth2/userinfo` |

`/t/{slug}/oauth2/authorize` requires a Gateway session; with none it returns
`401 login_required` so the frontend can drive login and retry. PKCE (S256) is required
for public clients.

## Multi-tenancy

Gateway is multi-tenant (row-level isolation). Every portal, admin, and OIDC endpoint
lives under `/t/{slug}/…`; a `default` tenant is seeded so single-tenant setups just use
`/t/default`. Each tenant has its own users, clients, sessions, tokens, MFA, consents, and
audit log — a session or client from one tenant is never valid in another.

Provision tenants with the global super-admin API (guarded by `GATEWAY_ADMIN_TOKEN`, mounted
outside `/t/{slug}`):

```bash
curl -X POST http://localhost:8080/api/provisioning/tenants \
  -H "X-Admin-Token: $GATEWAY_ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"slug":"acme","name":"Acme Inc"}'

curl http://localhost:8080/api/provisioning/tenants -H "X-Admin-Token: $GATEWAY_ADMIN_TOKEN"
```

The admin API (`/t/{slug}/api/admin/…`) is scoped to its tenant. Each tenant also has its own
signing-key set and OIDC issuer (`iss = {GATEWAY_ISSUER}/t/{slug}`), with per-tenant discovery
and JWKS — a token minted for one tenant is rejected at another's endpoints.

## Deploy

```bash
docker compose up --build
```

Brings up Postgres, Redis, and Gateway. Configure via the environment variables above.

## Modules

| Module | Responsibility |
|--------|----------------|
| `:app` | Ktor entrypoint, DI (Koin), route mounting — the only runnable module |
| `:common` | Crypto/token helpers, typed errors (framework-free) |
| `:domain` | Domain models + port interfaces |
| `:persistence` | Exposed tables, repositories, Flyway migrations, Hikari pool |
| `:session` | Server-side session issue/validate/revoke |
| `:auth-local` | Argon2id hashing, registration, password auth |
| `:auth-external` | External IdP connectors + account linking |
| `:mfa` | TOTP + recovery codes |
| `:oidc-provider` | Discovery, JWKS, JWT issuance, OIDC flow |
| `:audit` | Append-only audit log |
| `:admin-api` | Management API for the admin UI |
| `:detekt-rules` | Custom detekt rules |

Dependency direction: `:app` → feature modules → `:domain` / `:common`; `:persistence`
implements `:domain` ports. The core stays framework-free and testable.

## Security defaults

- Passwords hashed with Argon2id (64 MiB, t=3).
- Session tokens are 256-bit random; only their SHA-256 is stored. Cookies are
  HttpOnly + SameSite=Lax + Secure (in prod).
- OIDC uses RS256 signing, PKCE-first, refresh-token rotation with reuse detection.
- TOTP secrets encrypted at rest (AES-256-GCM); recovery codes hashed and single-use.
- Secrets come from the environment — none live in the repo.
