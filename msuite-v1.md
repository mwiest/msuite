# Msuite v1 — ToDo (tech validation)

See `msuite.md` for vision and architecture.

**Goal:** the thinnest app that exercises the whole shared layer end-to-end. ToDo features are almost incidental; the real deliverable is proving sync + E2E + pairing across two devices. Optimize for tech validation, not users. Web app excluded.

**Sequencing posture (OQ-15):** v1 is deliberately **infra-first** — the shared sync + E2E + pairing layer *is* the deliverable, so building it first is building the point. This is a **proof-of-concept, not a product** (no adoption goal — OQ-13); reminders and other app polish are deferred (OQ-14) because they prove none of the thesis. Scope is contained by the de-risking sequence below + strict minimalism (build only door-openers, never deferred machinery).

## `core` responsibilities

| Module | Must provide |
|---|---|
| `model` | Domain types, stable record IDs (UUID) + **stable opaque field/op IDs** (names display-only), op definitions, data-model version header (=1), identity `upcast` hook (OQ-6); batch/snapshot encoding via **kotlinx.serialization + ProtoBuf** behind an encoder interface (see `msuite.md` → *Batch encoding*) |
| `storage` | Local persistence (candidate: SQLDelight/KMP, targets **`android` + `jvm` only — `js` dropped**, OQ-12), op-log store, materialized current-state view with per-field `(value, HLC, deviceId)` **incl. opaque retention of unknown field IDs** (OQ-6); at rest relies on OS sandbox + FBE, root stored KeyStore-wrapped (OQ-11) |
| `sync` | HLC clock, op-log append/merge, LWW reducer behind `merge()`, backend-plug interface (`put`/`list`/`get`) + content-addressed batch naming + shipped adapters |
| `crypto` | 256-bit symmetric root (identity, no keypair — OQ-8); per-batch `HKDF-SHA256(root, salt)` → AES-256-GCM E2E of op-batches with authenticated header (`salt` + data-model version + `keyGen` id); single-QR pairing payload (carries the root); BIP39 recovery encode/decode — via **cryptography-kotlin** behind `core/crypto` (OQ-10) |
| `design` | Material 3 theme + shared Compose components |

## Minimal ToDo feature set

- Create / rename /delete lists (one "My Tasks" list is non-deletable and the default, exists from the start)
- Create (via a FAB) / edit (via touch on name) / delete (via ... in edit more) and complete (via checkbox in list or button in edit more) tasks
- Task fields: title, optional notes, optional due date, starred state, done state
- Optional due date (display only)
- Reorder lists and tasks within a list (manual order only; `position` is an LWW fractional-index string — see `msuite.md` OQ-4)
- Settings: theme, choose + configure backend plug

**Out of v1:** reminders/notifications (OQ-14 — deferred because they prove none of the shared-layer thesis; strategy noted for later: `AlarmManager`/`WorkManager` + OEM caveats, no FCM), subtasks, recurring tasks, attachments, web app.

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
| 8 | `server/` dumb relay (Ktor/Kotlin, reflection-averse) + relay plug | Live self-hosted sync |
| 9 | Cloud deploy template for the relay | Non-technical hosting path |

Steps 1–4 are plaintext (any device can merge); crypto (6) then pairing (7) come after manual sync (5) proves the merge itself.

**Step 5 plug shape:** the v1 "manual" plug is a *single-file export/import* holding one full-state snapshot (materialized per-field state `(value, HLC, deviceId)`). Device A exports, B imports and merges `local ⊕ snapshot` — this exercises the LWW reducer (a snapshot is a delta over every field, so delta-merge is a strict subset). The append-only, multi-object naming scheme is pinned now (OQ-3) but first *used at runtime* by the folder/relay plugs at step 8. See `msuite.md` → *Plug contract*.

## Milestone

**Two devices, paired by QR, syncing todos through a self-hosted relay that sees only ciphertext.** That validates the suite thesis.

## Library constraints (F-Droid)

- QR scanning: FOSS lib (ZXing / zxing-cpp), **not** Google ML Kit.
- No FCM, no GMS — local-only, sync via plugs.
- SDK levels: `minSdk` 26 (Android 8.0 — clean KeyStore/FBE baseline, minimal legacy shims); `compileSdk`/`targetSdk` track latest stable.
- Testing: **kotest-property** for the merge/HLC simulator (OQ-7) — KMP `commonTest`, runs on the **`jvm` target** (the reason `jvm` is kept — OQ-12), test-only (F-Droid/no-Google rules don't apply to test deps).
- Crypto: **cryptography-kotlin** (whyoleg), not Tink (Google) or libsodium (native) — see OQ-10. AES-256-GCM + HKDF-SHA-256 + SHA-256; **don't roll your own**.
- KMP web-readiness (OQ-12): the `js`/`wasm` target is **not built in v1**, but every core lib is chosen web-capable so re-adding it is config, not a rewrite — SQLDelight (sql.js/wasm driver), cryptography-kotlin (WebCrypto provider), Compose MP (wasmJs/Canvas), Ktor client (fetch engine). Platform-specific bits stay behind interfaces (SAF→File System Access API, KeyStore→WebCrypto+IndexedDB, camera QR→BarcodeDetector, alarms→Service Worker). See `msuite.md` OQ-12 for the full audit.
