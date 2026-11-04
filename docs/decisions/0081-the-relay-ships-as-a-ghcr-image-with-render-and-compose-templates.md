# 0081: The relay ships as a GHCR image with a Render Blueprint and a Compose template

Date: 2026-11-04. Status: accepted. Extends 0017 and 0079.

## Context

v1 step 10 must show a non-technical hosting path for the relay. The relay needs a persistent disk
(filesystem blobs, 0017) and someone else to terminate TLS (0079); no free PaaS tier offers both.
Hosts also differ in how they pick the listen port: most PaaS inject `$PORT`.

## Decision

- CI builds one container image from the `:server:installDist` output on a JRE and publishes it as
  `ghcr.io/mwiest/msuite-relay`; templates deploy that image and never build from source.
- The non-technical path is a Render Blueprint (`render.yaml`) behind a "Deploy to Render" button:
  an image-backed web service on the Starter plan with a disk, TLS by Render.
- Self-hosters get `deploy/compose/`: the image behind Caddy, which obtains certificates itself.
- `RelayConfig` reads `MSUITE_RELAY_PORT`, then `PORT`, then 8080. A set but blank
  `MSUITE_RELAY_ALLOW_GROUPS` stops the relay at start instead of denying every group, so a skipped
  deploy-form field fails loudly. The Blueprint prompts for the allowlist and pins Frankfurt.

## Rejected

- Building from source on the host: slow and memory-hungry on small instances, and the Gradle build
  configures the Android modules too.
- Fly.io: cheaper, but deploying needs the `flyctl` CLI, which is not a non-technical path.
- Compose alone: needs SSH, a domain and DNS.
- Railway or Koyeb templates: the template lives in their dashboard, not in this repo.
- Keeping a blank allowlist as "deny every group": a skipped form field would give a relay that
  refuses everyone, which the app can only report as a refusal.

## Consequences

A deploy costs the host's paid plan (Render Starter plus the disk). Render doesn't pull a newer
`latest` by itself; the operator redeploys to update. The image is the same artifact for every
host, so a GraalVM native image (0017) later only swaps the image's contents.
