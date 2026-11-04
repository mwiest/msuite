# Plan: v1 step 10, a cloud deploy template for the relay

Status: **slices 1 to 4 done, the image is public on GHCR; slice 5, the real deploy, is next.**
Started 2026-11-04. Parent: [`v1-todo.md`](v1-todo.md) step 10.

## Goal

Someone who doesn't run servers can get an `https://` relay URL from a button and a form, and two
devices sync through it. A self-hoster with a VPS gets the same relay behind automatic TLS from one
Compose file. Both run the same prebuilt image.

## Decisions

- Render Blueprint for the non-technical path, Docker Compose with Caddy for a VPS; one prebuilt
  image on GHCR built by CI; the relay falls back to `$PORT` (decision 0081).

## Slices

1. [x] **`PORT` fallback.** `MSUITE_RELAY_PORT` wins, then `PORT`, then 8080. Checked by
   `RelayConfigTest`. Landed: `8137002`.
2. [x] **Image and its workflow.** `server/Dockerfile` packs the `:server:installDist` output on a
   JRE; `.github/workflows/relay-image.yml` builds it on every PR, smoke-tests `/health` inside the
   container, and pushes `ghcr.io/mwiest/msuite-relay` from `main` and `v*` tags. Landed: `755dc36`,
   `738dccf` (retry on connection resets while the JVM starts), `7810236`; green on 2026-11-04.
3. [x] **Render Blueprint.** `render.yaml` (image-backed web service, Starter plan, 1 GB disk at
   `/data`, health check, registration allowlist prompted) and a "Deploy to Render" button. A
   set-but-blank allowlist now stops the relay instead of denying everyone, so a skipped form
   field can't produce a relay that refuses every group; Frankfurt region. Landed: `304c265`.
4. [x] **Compose + Caddy.** `deploy/compose/` with `compose.yaml`, `Caddyfile` and `.env.example`;
   the guide in `deploy/README.md`. CI brings the stack up with Caddy's internal CA on `localhost`
   and reads `/health` over HTTPS. Landed: `36bd752`; green on 2026-11-04.
5. [ ] **Real deploy.** Deploy from the button on a real account; two devices pair and sync through
   the `https://` URL; record it in `testing.md`.

## Open questions

None.

## How to verify

The workflow is green and the image answers `/health`; slice 5's two-device check passes through a
Render-hosted relay over HTTPS.
