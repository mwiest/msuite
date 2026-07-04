# Msuite v1 — ToDo (tech validation)

See `msuite.md` for vision and architecture.

**Goal:** the thinnest app that exercises the whole shared layer end-to-end. ToDo features are almost incidental; the real deliverable is proving sync + E2E + pairing across two devices. Optimize for tech validation, not users. Web app excluded.

## `core` responsibilities

| Module | Must provide |
|---|---|
| `model` | Domain types, stable record IDs (UUID) + **stable opaque field/op IDs** (names display-only), op definitions, data-model version header (=1), identity `upcast` hook (OQ-6) |
| `storage` | Local persistence (candidate: SQLDelight/KMP), op-log store, materialized current-state view with per-field `(value, HLC, deviceId)` **incl. opaque retention of unknown field IDs** (OQ-6); at rest relies on OS sandbox + FBE, root stored KeyStore-wrapped (OQ-11) |
| `sync` | HLC clock, op-log append/merge, LWW reducer behind `merge()`, backend-plug interface (`put`/`list`/`get`) + content-addressed batch naming + shipped adapters |
| `crypto` | 256-bit symmetric root (identity, no keypair — OQ-8); per-batch `HKDF-SHA256(root, salt)` → AES-256-GCM E2E of op-batches with authenticated header (`salt` + data-model version + `keyGen` id); single-QR pairing payload (carries the root); BIP39 recovery encode/decode — via **cryptography-kotlin** behind `core/crypto` (OQ-10) |
| `design` | Material 3 theme + shared Compose components |

## Minimal ToDo feature set

- Create / edit / delete tasks (title, optional notes, done state)
- Optional due date (display only)
- Multiple lists + reorder within a list (manual order only; `position` is an LWW fractional-index string — see `msuite.md` OQ-4)
- Local search
- Settings: theme, choose + configure backend plug

**Out of v1:** reminders/notifications, subtasks, recurring tasks, attachments, web app.

## Build order (de-risking sequence)

| # | Step | Proves |
|---|---|---|
| 1 | `core/model` + `core/storage`: local DB, op-log, materialized state | Data layer works |
| 2 | ToDo app single-device (CRUD) on top | Usable offline app exists |
| 3 | HLC + ordered op-log | Ordering robust to clock skew |
| 4 | LWW reducer behind `merge()` (total order `(pt, c, deviceId)`; concurrent per-field loss accepted — OQ-5) **+ merge/HLC simulator Layer 1** (OQ-7) as the merge gate | Deterministic merge, swappable later — *and property-proven* |
| 5 | Backend-plug interface (`put`/`list`/`get`) + manual plug (single full-state snapshot file, export/import); **harness Layer 2** wraps the real plug | Merge across 2 devices, in plaintext |
| 6 | `crypto`: E2E encrypt/decrypt of op-batches; **harness Layer 3** (convergence holds through encryption) | Data opaque to any server |
| 7 | QR pairing (single QR carries the root — OQ-8) + recovery key | Key distribution without accounts |
| 8 | `server/` dumb relay + relay plug | Live self-hosted sync |
| 9 | Cloud deploy template for the relay | Non-technical hosting path |

Steps 1–4 are plaintext (any device can merge); crypto (6) then pairing (7) come after manual sync (5) proves the merge itself.

**Step 5 plug shape:** the v1 "manual" plug is a *single-file export/import* holding one full-state snapshot (materialized per-field state `(value, HLC, deviceId)`). Device A exports, B imports and merges `local ⊕ snapshot` — this exercises the LWW reducer (a snapshot is a delta over every field, so delta-merge is a strict subset). The append-only, multi-object naming scheme is pinned now (OQ-3) but first *used at runtime* by the folder/relay plugs at step 8. See `msuite.md` → *Plug contract*.

## Milestone

**Two devices, paired by QR, syncing todos through a self-hosted relay that sees only ciphertext.** That validates the suite thesis.

## Library constraints (F-Droid)

- QR scanning: FOSS lib (ZXing / zxing-cpp), **not** Google ML Kit.
- No FCM, no GMS — local-only, sync via plugs.
- Testing: **kotest-property** for the merge/HLC simulator (OQ-7) — KMP `commonTest`, test-only (F-Droid/no-Google rules don't apply to test deps).
- Crypto: **cryptography-kotlin** (whyoleg), not Tink (Google) or libsodium (native) — see OQ-10. AES-256-GCM + HKDF-SHA-256 + SHA-256; **don't roll your own**.
