# Deploying Gateway

The stack runs from images published to the GitHub Container Registry (GHCR):

- `ghcr.io/gatewayauth/gateway` — Ktor auth backend (this repo)
- `ghcr.io/gatewayauth/keychain` — Nuxt SPA portal ([Keychain repo](https://github.com/Gatewayauth/Keychain))

`docker-compose.yml` here references both. `build:` is retained on each service
for local development; a deploy host uses the images instead.

## Images & CI

Each repo has a `docker-publish.yml` workflow that builds and pushes on:

- push to `main` → `:latest` and `:sha-<short>`
- push of a `vX.Y.Z` tag → `:X.Y.Z` and `:X.Y`

It authenticates with the built-in `GITHUB_TOKEN` (`packages: write`); no PAT
needed.

**First publish makes the GHCR package private.** Before a deploy host can pull
anonymously, set each package's visibility to **public**
(Org → Packages → package → Package settings → Change visibility), or on the
host run `docker login ghcr.io` with a token that has `read:packages`.

## Run from published images

```bash
cd Backend
docker compose pull
docker compose up -d
```

Pin versions instead of `latest` with the tag env vars:

```bash
GATEWAY_TAG=v0.1.0 KEYCHAIN_TAG=v0.1.0 docker compose up -d
```

- gateway API → host `:8080`
- keychain SPA → host `:3000`

The Keychain image is OSS and does **not** bake the API base. It is set at
container start from `NUXT_PUBLIC_API_BASE` / `NUXT_PUBLIC_TENANT_SLUG` (see the
`keychain` service env in `docker-compose.yml`). `NUXT_PUBLIC_API_BASE` is read
by the **browser**, so it must be the gateway URL reachable from the end user's
machine — behind a reverse proxy that is your public HTTPS origin (e.g.
`https://auth.example.com`), not an internal container hostname.

## Reverse proxy (not part of compose)

Put a TLS-terminating reverse proxy in front. It is intentionally left out of
`docker-compose.yml` — bring your own (Caddy, nginx, Traefik). Route:

- `/` → `keychain:80` (the SPA)
- API paths (`/oauth`, `/t/{slug}`, `/admin`, `/swagger`, …) → `gateway:8080`

Minimal Caddy example:

```caddy
auth.example.com {
    handle /oauth*  { reverse_proxy gateway:8080 }
    handle /t/*     { reverse_proxy gateway:8080 }
    handle /admin*  { reverse_proxy gateway:8080 }
    handle /swagger* { reverse_proxy gateway:8080 }
    handle          { reverse_proxy keychain:80 }
}
```

Set `NUXT_PUBLIC_API_BASE=https://auth.example.com` for keychain so the SPA
calls the API through the same public origin.

## Production hardening

Before any real deployment, override the compose defaults:

- **`GATEWAY_ADMIN_TOKEN`** and **`GATEWAY_ENC_KEY`** — rotate off the
  `change-me-*` placeholders to strong secrets.
- **`GATEWAY_COOKIE_SECURE=true`** — once served over HTTPS.
- **`GATEWAY_ISSUER`** — the public HTTPS URL (e.g. `https://auth.example.com`),
  not `http://localhost:8080`.
- Database credentials (`POSTGRES_*` / `GATEWAY_DB_*`) — move to real secrets and
  a managed/persistent Postgres rather than the bundled dev instance.
