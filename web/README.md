# Msuite Web (placeholder)

**Status: not started — deferred past v1.**

This folder is a placeholder for the web version of Msuite apps.

## Intended role

A **full app experience mirroring the native app** — for when the phone isn't around or is cumbersome to reach. Not a read-only viewer.

## Planned approach

- Reuse the `core/*` KMP modules (model, storage, sync, crypto) — the sync/merge/E2E logic must be identical to native so the two never diverge.
- Own UI layer initially (do **not** bet v1 on Compose-for-Web/wasm maturity). Revisit shared Compose UI once that target stabilizes.
- Local persistence in the browser (IndexedDB) as the counterpart to the app's local storage.
- Same transport tiers as native (Tier 0–3), same dumb relay, same E2E keys via QR/recovery-key pairing.

## Not doing

- The "drop an HTML file in Dropbox" share-link concept (share links can't write back to the folder, storage is origin-scoped, public links have no auth). See `../msuite.md`.

See the root `msuite.md` for the full spec and rationale.
