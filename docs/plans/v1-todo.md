# Plan: v1, a ToDo app that validates the shared sync layer

Status: **steps 0 and 1 done; step 2 (merge core) in progress: HLC (`8677b8d`), LWW
`merge()` (`53b6a7f`), `keyBetween` (`5d1ddae`) and the encoder done; the simulator is next.**
Started 2026-07-02; reordered 2026-10-11 (decision 0034).

## Goal

**Two devices, paired by QR, syncing todos through a synced folder that holds only ciphertext**
(decision 0042); the relay follows after the milestone. That validates the suite's thesis
(decision 0013). The ToDo features are almost incidental: v1 is the thinnest app that exercises
the whole shared layer (sync, E2E, pairing) end to end. Optimised for tech validation, not
users. No web app.

## Decisions

- Infra-first, starting with the pure merge core; only door-openers, never deferred machinery
  (decision 0034).
- Steps 2 to 5 are plaintext; crypto (6) and pairing (7) come after manual sync (5) has proven the
  merge. The plaintext export of step 5 is debug-only and becomes encrypted in step 6.
- The v1 shipping plug is the manual single-file export/import of one full-state snapshot
  (decision 0003, `architecture.md` → Backend plugs).
- Storage is one generic `field_state` table (decision 0035); no local op-log (decision 0036).
- App code lives in `apps/todo/shared` (KMP); `apps/todo/android` is a thin shell (decision 0037).
- "My Tasks" has a fixed ID (decision 0031). Tasks of a deleted list are hidden (decision 0032). A
  device joining by pairing keeps and shares its data (decision 0033).
- No reminders or notifications (decision 0014). Also out: subtasks, recurring tasks, attachments,
  web.
- UI text in resources, English and German (decision 0027).

## ToDo feature set

- Lists: create, rename, delete. One list, "My Tasks", exists from the start, is the default, and
  can't be deleted. Deleting a list hides its tasks.
- Tasks: create (FAB), edit (tap the name), delete (from the edit screen's overflow menu),
  complete (checkbox in the list, or a button in the edit screen).
- Task fields: title, optional notes, optional due date (display only), starred, done.
- Reorder lists, and tasks within a list. Manual order only (`position` field, decision 0004).
- Settings: theme; choose and configure the backend plug.

## Slices

Each step may take several commits; a step that grows big gets its own plan.

0. [x] **Build housekeeping.** Docs moved into `docs/` (Voclet layout); `build-logic` convention
   plugins; daemon pinned to JDK 21; Spotless and detekt; GitHub Actions and Renovate. Checked by a
   green `./gradlew spotlessCheck detekt allTests assembleDebug :server:jar` locally; CI not run
   yet. Landed: `241a9ea`, `b7a0d47`, `95e7b67`, `8f985ba`, `3840a9b`, `3fe8207`.
1. [x] **Toolchain and KMP app module.** Gradle 9.8, AGP 9.4 (KMP Android plugin, built-in
   Kotlin), Kotlin 2.4, Compose Multiplatform 1.12, kotest 6, compileSdk 37 and the unwired pins at
   their latest stable; `apps/todo` split into `shared` and `android`; multiplatform navigation
   pinned. Checked by the same green build. Landed: `282f4c9`, `f6e2e16`, `8ea7bc2`.
2. [ ] **Merge core** (`core/model`, `core/sync`, pure Kotlin). Stable field IDs; HLC with the
   24 h clamp (decision 0040); `keyBetween` with injectable jitter; the generic cell type; LWW
   `merge()`; the ProtoBuf encoder; simulator Layer 1 as the gate (`testing.md`). Proves:
   deterministic merge, robust to clock skew, property-tested.
3. [ ] **Storage.** SQLDelight in `core/storage`: `field_state`, ingested batch keys, per-plug
   watermarks. Proves: state persists and merges through the database.
4. [ ] **Single-device ToDo app.** The feature set above in `apps/todo/shared`: projection over
   `field_state`, the read-time rules, screens. UI text mechanism first (see Open questions).
   Simulator invariants 14 to 16. Proves: a usable offline app exists.
5. [ ] **Backend-plug interface and the manual plug.** `put`/`list`/`get`; export/import of one
   full-state snapshot (plaintext, debug-only); simulator Layer 2. Proves: merge across two
   devices.
6. [ ] **E2E encryption of batches** in `core/crypto`: envelope with the object key in the AAD
   (decision 0029); the manual export becomes encrypted; root at rest via KeyStore (decision
   0039); simulator Layer 3. Proves: data is opaque to any server.
7. [ ] **QR pairing and recovery key.** One QR carrying the root; a joining device merges its data
   (decision 0033); BIP39 recovery; a copied key is marked sensitive and cleared after 60 s
   (decision 0041). Proves: key distribution without accounts.
8. [ ] **Synced-folder plug (milestone).** SAF plug writing the multi-object keys (decision 0029)
   into a folder that Syncthing, Nextcloud or Dropbox syncs; its logic behind a file-system
   interface; simulator Layer 2 with invariant 12 on plain files (decision 0042). Proves: live
   multi-device sync through a backend that sees only ciphertext.
9. [ ] **Relay and relay plug.** `server/` as a Ktor CIO binary with namespace tokens (decision
   0030), plus the plug that talks to it. Proves: live self-hosted sync without a sync tool.
10. [ ] **Cloud deploy template for the relay.** Proves: a non-technical hosting path exists.

## Open questions

- UI text mechanism: the UI is in `commonMain` (decision 0037), so Compose Multiplatform resources
  is the natural fit and Android `strings.xml` is out. Confirm when step 4 starts.
- Where the ToDo projection and its tests live: `apps/todo/shared` is android-only, but tests run
  on `jvm` (decision 0038). Either add a `jvm` target to `shared`, or put the non-UI logic in its
  own KMP module (android + jvm). Decide when step 4 starts.
- Whether the pairing QR also carries the backend configuration, or the joining device sets it up
  itself. Decide at step 7.

## How to verify

The milestone on two real devices: pair by QR, edit on both (including while offline), sync
through a folder shared by Syncthing, and both show the same lists. The folder holds only
ciphertext, under keys per decision 0029. Steps 9 and 10 repeat this through a self-hosted relay.
The simulator (all three layers) is green in CI.
