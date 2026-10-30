# Plan: v1, a ToDo app that validates the shared sync layer

Status: **steps 0 to 8 done, code and device checks; step 9, the relay, is next.**
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
  merge. The plaintext export of step 5 is a proof of concept until it becomes encrypted in step 6.
- The v1 shipping plug is the manual single-file export/import of one full-state snapshot
  (decision 0003, `architecture.md` → Backend plugs).
- Storage is one generic `field_state` table (decision 0035); no local op-log (decision 0036).
- App code lives in `apps/todo/shared` (KMP); `apps/todo/android` is a thin shell (decision 0037).
- "My Tasks" has a fixed ID (decision 0031). Tasks of a deleted list are hidden (decision 0032). A
  device joining by pairing keeps and shares its data (decision 0033).
- No reminders or notifications (decision 0014). Also out: subtasks, recurring tasks, attachments,
  web.
- UI text in Compose Multiplatform resources, English and German (decisions 0027, 0047).
- The projection, rules and commands live in `apps/todo/domain` (android + jvm) and are tested
  there; `shared` holds the Compose UI (decision 0051).
- The UI refreshes on change ticks from `Replica` (decision 0049); the Android shell creates the
  SQLite driver (decision 0050).

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
2. [x] **Merge core** (`core/model`, `core/sync`, pure Kotlin). Stable field IDs; HLC with the
   24 h clamp (decision 0040); `keyBetween` with injectable jitter; the generic cell type; LWW
   `merge()`; the ProtoBuf encoder; simulator Layer 1 as the gate (`testing.md`). Proves:
   deterministic merge, robust to clock skew, property-tested. Landed: `8677b8d`, `53b6a7f`,
   `5d1ddae`, `7c3c330`, `bd2c043`; decisions 0043 to 0045.
3. [x] **Storage.** SQLDelight in `core/storage` (decision 0046; sqlite-jdbc for `jvmTest` only):
   `field_state`, ingested batch keys and watermarks per plug ID, HLCs stored sign-flipped so SQL
   compares them unsigned; `Replica` in `core/sync` starts the `HlcClock` from the highest stored
   HLC and runs every operation under a `Mutex` in one transaction. Text columns use SQLite's
   default binary collation; positions sit inside value blobs, so step 4 sorts them in Kotlin
   (decision 0044). Proves: state persists and merges through the database. Landed: `0f1b455`,
   `e351d69`.
4. [x] **Single-device ToDo app.** The feature set above: projection over `field_state`, the
   read-time rules and commands in `apps/todo/domain`, screens in `apps/todo/shared`. Simulator
   invariants 14 to 16. Proves: a usable offline app exists. Decisions 0049 to 0064; the wiring
   and UI are in `architecture.md`. Checked on an emulator. Landed: `674ab59` to `292e00e`.
5. [x] **Backend-plug interface and the manual plug.** `put`/`list`/`get`; export/import of one
   full-state snapshot (plaintext; a proof of concept until step 6 encrypts it); simulator Layer 2.
   Proves: merge across two devices. Decisions 0066 to 0069; the plug design is in
   `architecture.md` → Backend plugs and Compaction. Checked by hand between a phone and an
   emulator (export and import both ways, an imported file twice, and a deletion that crossed).
   Landed: `914580e` to `5789870`.
6. [x] **E2E encryption of batches** in `core/crypto`: envelope with the object key in the AAD
   (decision 0029); the manual export becomes encrypted; root at rest via KeyStore (decision
   0039); simulator Layer 3. Proves: data is opaque to any server. Code landed: `c4c65f5`,
   `f8c3cf2`, `a4107c5`, `1e76ec5`, `a7e3874`, `8456bbb`; decisions 0070, 0071. `allTests` and
   Layer 3 green on 2026-10-17; checked on 2026-10-18 on an emulator (the root survives a restart,
   the exported file holds no plaintext) and between two devices (the file fails honestly —
   different roots, so pairing it is step 7's job).
7. [x] **QR pairing and recovery key.** One QR carrying the root and the group's backend
   descriptor; a joining device merges its data (decision 0033); BIP39 recovery; a copied key is
   marked sensitive and cleared after 60 s (decision 0041). Proves: key distribution without
   accounts. Landed: `71ce6ee` to `51cbcf6`, plus `e37b38b` (the scanner read ZXing's codewords,
   not the payload), `552eaaf` (the QR size) and `69415e7` (the pair-first hint). Checked by hand on
   2026-10-22 between a phone and an emulator, both Android 16: pair by QR, export/import both ways
   with each device's pre-pairing data kept and a deletion crossing, the words copied as a sensitive
   clip that cleared after 60 s, a fresh install restored from words and importing the group's
   export, and the German strings.
8. [x] **Synced-folder plug (milestone).** SAF plug writing the multi-object keys (decision 0029)
   into a folder that Syncthing, Nextcloud or Dropbox syncs; its logic behind a file-system
   interface; simulator Layer 2 with invariant 12 on plain files (decision 0042), then a delta
   mode over the real key grammar with span skip, coarse deltas and bookkeeping pruning,
   asserting invariants 18 and 19 (`architecture.md` → Compaction). Proves: live multi-device
   sync through a backend that sees only ciphertext. Landed: `49f3d12` to `e46c8a1`, then the device
   check's fixes `bc0ff76` to `e7c15d7`: document providers in the manifest (0076), keys naming
   their writer, batches sealed in the envelope (the folder had held plaintext), a failing folder
   that no longer crashes and shows in Settings, conflict copies and twin folders cleaned up
   (0078), and a publish counted only once listed (0077). Checked by hand 2026-10-27 to 2026-10-30
   on two Android 16 phones through Dropbox (`testing.md` -> Two-device check): simultaneous,
   staggered and racing writes, a 10-minute soak ending identical cell for cell, reboot and
   reinstall. Snapshots and pruning run in the simulator only; on devices they land with step 9.
9. [ ] **Relay and relay plug.** `server/` as a Ktor CIO binary with namespace tokens (decision
   0030), plus the plug that talks to it; capability-gated `delete` and writer-side pruning with
   the recovery rule (`architecture.md` → Compaction). Proves: live self-hosted sync without a
   sync tool.
10. [ ] **Cloud deploy template for the relay.** Proves: a non-technical hosting path exists.

## Open questions

Decided in step 7 (2026-10-18): the pairing QR carries identity and the group's backend
descriptor — the backend type today ("manual", nothing portable to carry), the folder type's
chooser trigger with the folder plug (step 8), portable config when a backend has it (the relay,
step 9). Recovery words are English BIP39 from an in-repo wordlist; the scanner is
zxing-android-embedded. Records 0072 to 0074.

Decided in step 5: a batch from a newer data-model version is held back, not merged (decision
0067); a batch that fails to decode is skipped and retried a bounded number of times, then flagged
corrupt (decisions 0068, 0069).

Decided in the step 6 plan: the data-model version moves to the envelope header, and the batch
body and the plaintext transport carry cells only (record 0070).

## How to verify

The milestone on two real devices: pair by QR, edit on both (including while offline), sync
through a folder shared by Syncthing, and both show the same lists. The folder holds only
ciphertext, under keys per decision 0029. Steps 9 and 10 repeat this through a self-hosted relay.
The simulator (all three layers) is green in CI.
