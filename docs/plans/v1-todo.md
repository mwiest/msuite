# Plan: v1, a ToDo app that validates the shared sync layer

Status: **slice 0 (build housekeeping) done; steps 1 to 9 not started.** Started 2026-07-02.

## Goal

**Two devices, paired by QR, syncing todos through a self-hosted relay that sees only
ciphertext.** That validates the suite's thesis (decision 0013). The ToDo features are almost
incidental: v1 is the thinnest app that exercises the whole shared layer (sync, E2E, pairing) end
to end. Optimised for tech validation, not users. No web app.

## Decisions

- Infra-first, in the de-risking order below; only door-openers, never deferred machinery
  (decision 0015).
- Steps 1 to 5 are plaintext; crypto (6) and pairing (7) come after manual sync (5) has proven the
  merge.
- The v1 shipping plug is the manual single-file export/import of one full-state snapshot
  (decision 0003, `architecture.md` → Backend plugs).
- No reminders or notifications (decision 0014). Also out: subtasks, recurring tasks, attachments,
  web.
- UI text in resources, English and German (decision 0027).

## ToDo feature set

- Lists: create, rename, delete. One list, "My Tasks", exists from the start, is the default, and
  can't be deleted.
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
   yet. Landed: `241a9ea`, `b7a0d47`, `95e7b67`, `8f985ba`, `3840a9b`, plus the follow-up commit
   that ticks this box.
1. [ ] **Data layer.** `core/model` and `core/storage`: SQLDelight database, op-log, materialized
   per-field state with opaque unknown fields, ProtoBuf encoder. Proves: the data layer works.
2. [ ] **Single-device ToDo app.** The feature set above on top of step 1, offline. Decide the UI
   text mechanism first (decision 0027). Proves: a usable offline app exists.
3. [ ] **HLC and ordered op-log.** Proves: ordering is robust to clock skew.
4. [ ] **LWW reducer behind `merge()`**, plus simulator Layer 1 as the merge gate
   (`testing.md`). Proves: deterministic merge, swappable later, property-tested.
5. [ ] **Backend-plug interface and the manual plug.** `put`/`list`/`get`; export/import of one
   full-state snapshot; simulator Layer 2. Proves: merge across two devices, in plaintext.
6. [ ] **E2E encryption of batches** in `core/crypto`; simulator Layer 3. Proves: data is opaque to
   any server.
7. [ ] **QR pairing and recovery key.** One QR carrying the root; BIP39 recovery. Proves: key
   distribution without accounts.
8. [ ] **Relay and relay plug.** `server/` as a Ktor CIO binary plus the plug that talks to it;
   the multi-object batch names are first used here. Proves: live self-hosted sync.
9. [ ] **Cloud deploy template for the relay.** Proves: a non-technical hosting path exists.

## Open questions

- UI text mechanism: Compose Multiplatform resources or Android `strings.xml`. Decide at step 2.
- Libraries pinned but not yet wired (SQLDelight, cryptography-kotlin, Ktor, Navigation Compose,
  multiplatform-settings, Kermit) may be outdated by the time their step starts; check for newer
  versions when wiring them.

## How to verify

The goal scenario on two real devices: pair by QR, edit on both (including while offline), sync
through a self-hosted relay, and both show the same lists. The relay's storage holds only
ciphertext with names per decision 0003. The simulator (all three layers) is green in CI.
