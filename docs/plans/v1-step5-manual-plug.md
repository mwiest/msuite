# Plan: v1 step 5, the backend-plug contract and the manual plug

Status: **all slices landed; the two-device check is pending.** Started 2026-10-16. A slice of
[`v1-todo.md`](v1-todo.md) step 5.

## Goal

Two devices prove the merge across a boundary: one exports its whole state to a file, the other
imports it and both converge. Along the way the append-only `put`/`list`/`get` contract that the
live plugs (synced folder, relay) will implement is frozen and contract-tested. The manual export is
plaintext and debug-only until step 6 encrypts it (decisions 0003, 0042).

## Decisions

- The manual plug is a snapshot export/import, not a `put`/`list`/`get` plug: it has no list and no
  object names (decision 0066). The contract is still defined here and tested with a fake.
- `ingest` holds back a batch from a newer data-model version, like a far-future batch (decision
  0067): it is not marked ingested and is retried after an app update.
- A plug that can't decode a batch skips it and retries it later; it is never marked ingested
  (decision 0068).
- Layer 2 wraps the real plug contract in the `Cluster` harness, so step 8 swaps a plain-file
  folder in with nothing else changing (decision 0057, `testing.md`).
- The export is plaintext and debug-only until step 6 (v1 plan; architecture.md → Backend plugs).
- The snapshot merges through the same `ingest` reducer as a delta (invariant 11), so it is one
  code path (decision 0002).

## Slices

Each slice is one commit, builds, and leaves the build green.

1. [x] **The plug contract.** `BackendPlug` (`put`/`list`/`get`) in `core/sync` and an in-memory
   `MemoryPlug` in `core/sync-testing`; a contract test: create-only, `get` round-trips, `list`
   returns a prefix's keys and no partial object, keys never clash. Decision 0066. Landed:
   `914580e`.
2. [x] **Snapshots and the manual plug.** `ManualSync.export()` builds the whole state into a
   `Batch` and `import(bytes)` merges through a `Replica.mergeRemote` path shared with `ingest`
   (over `ProtoBatchEncoder`); a bad file raises `MalformedBatch`. Checks: two replicas converge
   through export/import, importing twice changes nothing, a far-future snapshot is deferred.
   Landed: `bfb438d`.
3. [x] **The version and decode seams.** `ingest` reports a batch from a newer data-model version
   as a distinct non-merged result and `Replica` doesn't mark it ingested; a plug decode failure is
   skipped. Decisions 0067, 0068. Checks in `IngestTest` and the Layer 2 store. Landed: `dbd8524`.
4. [x] **Simulator Layer 2.** `Cluster` syncs through a swappable store; `PlugStore` runs batches
   through a real `BackendPlug` and `ProtoBatchEncoder`; a simulation where devices publish whole
   snapshots converges (and re-publishing changes nothing). Invariant 12 on the contract. Landed:
   `9d43dce`.
5. [x] **Settings UI and the Android file picker.** An "Export"/"Import" section in
   `SettingsScreen`, launching the system create/open-document pickers through a platform interface
   in the shell; strings in English and German. Landed: `9ae8e50`. Emulator check: pending.

## Open questions

None: the two owed by the v1 plan are decided in 0067 and 0068. The pairing-QR question (does it
carry the backend config behind?) is step 7, not here.

## How to verify

`./gradlew :core:sync:jvmTest` is green, including the snapshot and Layer 2 tests. On an emulator,
export a snapshot from one device, import it on another, and both show the same lists.
