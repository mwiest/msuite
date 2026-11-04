# Hosting the msuite relay

The relay is a small server that stores your devices' encrypted batches; it never sees your data
(`docs/architecture.md` → Relay). Every option below runs the same image,
`ghcr.io/mwiest/msuite-relay`, and gives you an `https://` address to type into the app.

Before you start, on the device the others will pair with, open the app's **Settings**, choose
**Sync method → Relay** and note the **Group ID** shown there. The relay only lets the groups you
list create storage on it, so strangers who find its address can't fill it. To add a group later,
append its ID to `MSUITE_RELAY_ALLOW_GROUPS`, separated by a comma.

## Render (no server skills needed)

[![Deploy to Render](https://render.com/images/deploy-to-render-button.svg)](https://render.com/deploy?repo=https://github.com/mwiest/msuite)

1. Click the button and sign in to Render (or create an account).
2. Paste your group ID into `MSUITE_RELAY_ALLOW_GROUPS` and confirm. Render creates the relay in
   Frankfurt on the Starter plan with a 1 GB disk; a disk needs a paid plan.
3. When the service shows **Live**, copy its address (`https://msuite-relay-….onrender.com`).
4. On that device, paste the address into **Relay address** and tap **Connect**. Pair your other
   devices by QR; they pick up the relay from the code.

Updates: Render doesn't pull a newer image by itself. Choose **Manual Deploy → Deploy latest
reference** on the service to update.

## Your own server (Docker Compose)

For a VPS with Docker (Oracle Always-Free, Hetzner, a home server with ports 80 and 443 open) and
a domain whose DNS points at it. [Caddy](https://caddyserver.com) in front of the relay obtains and
renews the certificate.

```sh
git clone https://github.com/mwiest/msuite.git
cd msuite/deploy/compose
cp .env.example .env    # set RELAY_DOMAIN and MSUITE_RELAY_ALLOW_GROUPS
docker compose up -d
```

The address for the app is `https://` plus your domain. Update with
`docker compose pull && docker compose up -d`. The batches live in the `relay-data` volume.

## Configuration

| Variable | Default | Meaning |
|---|---|---|
| `MSUITE_RELAY_ALLOW_GROUPS` | unset: any group may register | Group IDs that may create storage; set but blank stops the relay from starting |
| `MSUITE_RELAY_PORT` | the host's `PORT`, else 8080 | Listen port |
| `MSUITE_RELAY_DATA` | `/data` in the image | Where the batches are stored |
| `MSUITE_RELAY_MAX_OBJECT_BYTES` | 16 MiB | Largest single batch |
| `MSUITE_RELAY_MAX_NAMESPACE_BYTES` | 1 GiB | Storage per group (Render: 256 MiB) |
