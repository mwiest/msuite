# Msuite v1 — ToDo (tech validation)

See `msuite.md` for vision and architecture.

**Goal:** the thinnest app that exercises the whole shared layer end-to-end. ToDo features are almost incidental; the real deliverable is proving sync + E2E + pairing across two devices. Optimize for tech validation, not users. Web app excluded.

## `core` responsibilities

| Module | Must provide |
|---|---|
| `model` | Domain types, stable IDs (UUID), op definitions |
| `storage` | Local persistence (candidate: SQLDelight/KMP), op-log store, materialized current-state view |
| `sync` | HLC clock, op-log append/merge, LWW reducer behind `merge()`, backend-plug interface + shipped adapters |
| `crypto` | Keypair gen, E2E encrypt/decrypt of op-batches, QR-pairing payload, recovery-key encode/decode |
| `design` | Material 3 theme + shared Compose components |

## Minimal ToDo feature set

- Create / edit / delete tasks (title, optional notes, done state)
- Optional due date (display only)
- Multiple lists + reorder within a list
- Local search
- Settings: theme, choose + configure backend plug

**Out of v1:** reminders/notifications, subtasks, recurring tasks, attachments, web app.

## Build order (de-risking sequence)

| # | Step | Proves |
|---|---|---|
| 1 | `core/model` + `core/storage`: local DB, op-log, materialized state | Data layer works |
| 2 | ToDo app single-device (CRUD) on top | Usable offline app exists |
| 3 | HLC + ordered op-log | Ordering robust to clock skew |
| 4 | LWW reducer behind `merge()` | Deterministic merge, swappable later |
| 5 | Backend-plug interface + manual plug (export/import) | Merge across 2 devices, in plaintext |
| 6 | `crypto`: E2E encrypt/decrypt of op-batches | Data opaque to any server |
| 7 | QR pairing + recovery key | Key distribution without accounts |
| 8 | `server/` dumb relay + relay plug | Live self-hosted sync |
| 9 | Cloud deploy template for the relay | Non-technical hosting path |

Steps 1–4 are plaintext (any device can merge); crypto (6) then pairing (7) come after manual sync (5) proves the merge itself.

## Milestone

**Two devices, paired by QR, syncing todos through a self-hosted relay that sees only ciphertext.** That validates the suite thesis.

## Library constraints (F-Droid)

- QR scanning: FOSS lib (ZXing / zxing-cpp), **not** Google ML Kit.
- No FCM, no GMS — local-only, sync via plugs.
