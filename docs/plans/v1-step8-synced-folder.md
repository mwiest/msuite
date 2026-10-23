# Plan: v1 step 8, the synced-folder plug (milestone)

Status: **not started.** Planned.

## Goal

Two devices, paired by QR, syncing todos through a synced folder that holds only ciphertext.
The folder is a folder the user already syncs with Syncthing, Nextcloud, Dropbox or similar.
The folder holds files under the multi-object keys of decision 0029; each file is one batch.
This is the v1 milestone (decision 0042): live multi-device sync through a backend that sees only
ciphertext. The relay follows as step 9.

## Decisions

- The folder plug's logic sits behind a pure-KMP `FileStore` interface, so the simulator runs it
  on `jvm` with plain `java.io.File` and Android wraps SAF (decision 0042).
- Each batch is one file; the namespace `{appId}/{groupId}` is nested subfolders; atomic publish
  writes `.tmp_*` then renames (architecture → Backend plugs).
- Sync cadence is foreground polling every 10 s while the app is visible, plus a manual "Sync now"
  button; no background service (v1 scope).
- Delta batching waits 10 s of idle before publishing, so a burst of edits becomes one batch
  (architecture → Compaction: "coarsely batched, per idle period").
- The manual export/import stays in Settings alongside the folder plug; it's a fallback for backups
  or when SAF is unavailable.
- The pairing QR's backend descriptor for the folder type carries no portable config; the joining
  device offers its own folder chooser (decision 0072).

## Slices

Each slice is one commit (or a few), builds, and leaves the build green.

1. [ ] **`FileStore` interface.** Pure KMP in `core/sync`: `read(path)`, `write(path, bytes)`,
   `list(prefix)`, `delete(path)`, `mkdir(path)`, `rename(from, to)`, `exists(path)`. After it
   lands: `<commit>`.
2. [ ] **Plain-file `FileStore` for tests.** In `core/sync-testing` or `core/sync/src/jvmTest`,
   wraps `java.io.File` in a temp directory. After it lands: `<commit>`.
3. [ ] **`FolderPlug` implementation.** Pure KMP in `core/sync`, implements `BackendPlug` over
   `FileStore`. Maps keys to nested subfolders (`{appId}/{groupId}/{name}`); atomic publish writes
   `.tmp_*` then renames; `list` returns children. After it lands: `<commit>`.
4. [ ] **Test `FolderPlug` with plain files.** Unit tests in `core/sync/src/jvmTest`: the plug
   contract (create-only, atomic, list returns only fully published keys). This validates invariant
   12 on plain files. After it lands: `<commit>`.
5. [ ] **Wire `FolderPlug` into simulator Layer 2.** The simulator's Layer 2 now runs with
   `FolderPlug(plainFileStore)` instead of `MemoryPlug`. Assert invariant 12: concurrent puts give
   distinct objects, list returns only fully published keys. After it lands: `<commit>`.
6. [ ] **Switch simulator to real `ObjectKey` grammar.** Replace the flat `sim/<hash>` keys with
   the real `ObjectKey` grammar of decision 0029 (spans, types, device IDs). The simulator now
   exercises the key structure. After it lands: `<commit>`.
7. [ ] **Span skip logic.** A reader that has ingested a writer's snapshot skips that writer's
   objects whose `hlcLo`..`hlcHi` span lies inside it (architecture → Compaction). No `delete`
   needed; fetching stays bounded. After it lands: `<commit>`.
8. [ ] **Coarse deltas.** The plug waits 10 s of idle before publishing a delta, so a burst of
   edits becomes one batch (architecture → Compaction). After it lands: `<commit>`.
9. [ ] **Bookkeeping pruning.** Prune `ingested_batch` and `unreadable_batch` rows whose keys are
   no longer in `list` (architecture → Compaction). After it lands: `<commit>`.
10. [ ] **Assert invariants 18 and 19.** After a writer publishes a snapshot and prunes its earlier
    objects, every reader converges on the surviving objects alone (invariant 18). A device that
    first syncs only after pruning converges to the same state, and each writer's object count stays
    bounded (invariant 19). After it lands: `<commit>`.
11. [ ] **SAF `FileStore` wrapper.** In `apps/todo/android`, wrap `DocumentFile` and
    `ContentResolver` into `FileStore`. Handle persistable URI permissions across restarts. After it
    lands: `<commit>`.
12. [ ] **Settings UI for the folder plug.** In `apps/todo/shared`, add a route to pick the synced
    folder (SAF picker), show sync status (last sync time, pending changes), and a manual "Sync now"
    button. The manual export/import stays as a fallback. After it lands: `<commit>`.
13. [ ] **Foreground polling.** A coroutine job syncs every 10 s while the app is visible (Lifecycle
    observer). A manual "Sync now" button is always available. After it lands: `<commit>`.
14. [ ] **Pairing QR backend descriptor.** The pairing QR carries the folder type (decision 0072);
    the joining device offers its own folder chooser. No portable config. After it lands:
    `<commit>`.
15. [ ] **Integration and device check.** Wire everything together: the app uses `FolderPlug` when
    configured. Checked on two real devices: pair by QR, edit on both (including while offline),
    sync through a folder shared by Syncthing, and both show the same lists. The folder holds only
    ciphertext, under keys per decision 0029. After it lands: `<commit>`.

## Open questions

- The `FileStore` interface needs to handle errors: what does a missing file return? `null` or an
  exception? (Planned: `read` returns `null` for missing, `write` throws on failure.)
- How should the app detect that the synced folder has changed (e.g., another device wrote to it)?
  Options: poll on a timer, or use SAF's `FileObserver` (not available in KMP). (Planned: poll on a
  10 s timer while visible.)
- Should the folder plug publish a snapshot on first sync (after pairing or after switching from
  manual), or only deltas? (Planned: a snapshot on first sync, then deltas.)

## How to verify

The milestone on two real devices (Android 16):

1. Pair by QR; both devices show the same lists.
2. Edit on both (including while offline); sync through a folder shared by Syncthing.
3. Both devices converge to the same state.
4. The folder holds only ciphertext, under keys per decision 0029
   (`{appId}/{groupId}/v{fmt}_{type}_{deviceId}_{hlcLo}_{hlcHi}_{hash}`).
5. After a snapshot and pruning, a device that joins late converges to the same state (invariants
   18, 19).
6. The simulator (Layer 2 with plain files, real key grammar, span skip, coarse deltas, pruning) is
   green in CI.
