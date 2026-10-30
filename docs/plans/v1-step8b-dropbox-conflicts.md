# Plan: v1 step 8b, Dropbox conflict-file investigation

Status: **In progress** (2026-10-30). Started 2026-10-27. F1, F2 key, F7, F8, F9 and lost publishes
fixed and verified on both devices; T2 and T3 done. Next: republish full state, T4, T1, close.
See [Next session](#next-session).

## Goal

Step 8's milestone check on two real devices (OnePlus Nord AC2003 and Motorola edge 70 pro,
Android 16, Dropbox as the synced folder) surfaced conflict files, lost folder access and
crashes. Done when two devices sync through Dropbox over a 10-minute soak with converged state,
no crash, no data-bearing duplicates, and every finding below is fixed or a recorded later todo.

## Fixed so far

| What | Commit | Verified |
|---|---|---|
| U1: Dropbox provider went invisible on the OnePlus; `<queries>` for `DOCUMENTS_PROVIDER` (decision 0076) | `bc0ff76` | Nord, without re-picking |
| F2: published keys named a peer's device id; now `replica.deviceId` | `5521754` | T2 round 1 |
| F9: folder sync uploaded **plaintext**; `LiveSync` now seals every batch in `BatchEnvelope` (AAD = key minus hash, hash over the ciphertext) and skips names that don't parse as keys | `891630f` | rollout 22:52, folder wiped and republished |
| F8: conflict copies `key (N)` / `key_<digits>` are deleted on pull while `key` is listed; `SafFileStore`'s post-write cleanup removed | `3eb5dac` | both devices 23:14, copies gone |
| F7: a failing plug no longer crashes; `LiveSync.healthy`, retry after the next pull; Settings shows "Can't reach the folder" | `1ff6131`, `582728d` | T3 re-run: logged, no crash |
| F1: `FolderPlug` adopts a twin `{gid}_<n>` / `{gid} (N)` into the group folder | `6c886c9` | both devices 22:50, Nord's lost batch recovered |
| Lost publishes: a key stays unconfirmed until listed, 3 misses rewind the watermark (decision 0077, migration 2) | `e7c15d7` | unit tests; migration ran on both devices |
| `PairingTest` didn't compile since `ea2d558` (`backendType`), so `allTests` was red | `891630f` | `allTests` green |

## Findings (condensed)

- **F1 folder-creation race:** both devices picking the Dropbox *root* and creating
  `todo/{gid}/` at once got two folders (`{gid}` and `{gid}_<n>`), each device writing into its
  own. Workaround: pick a dedicated parent (`msuite`). Whether it races with a dedicated parent
  too is T3.
- **F2 / echo:** a delta is every cell above the plug watermark, so a peer's batch whose HLC lands
  above the receiver's watermark is re-published by the receiver (the *echo*). Before `5521754`
  the echo carried the peer's key, which produced same-key publishes and the data-bearing `_N`
  copies. Now it is a duplicate under the receiver's key. **Decided 2026-10-28: keep the echo**,
  revisit with relay/multi-plug work (needs per-plug origin per cell).
- **F3 / U3 `(1)` copies are writer-created:** T2 round 2 showed each `(1)` in the *writer's*
  first `list()` after a single delete → createFile → write, 0 bytes, appearing asynchronously
  seconds later (2 of the Nord's 6 writes, 0 of the Moto's 3). Most likely Dropbox uploads the
  empty `createFile` revision and the content revision as two conflicting revisions. Not
  preventable through SAF; swept since `3eb5dac` (see [Later](#later)).
- **F4 Dropbox SAF flags lie:** `raw=324` claims `rename=true`, but `renameTo()` throws
  `FileNotFoundException: invalid path`; `copy=false` is true. Decision 0075's local-temp publish
  stands.
- **F5 pruning on a silently empty folder:** when `list()` wrongly returns nothing (F6), pruning
  wipes all `ingested_batch` rows; correctness holds (create-only keys, idempotent ingest), but
  recovery re-reads everything.
- **F6 three silent failure tiers of a SAF folder:** (1) provider invisible: `E ActivityThread:
  Failed to find provider info ...`, folder reads as empty; (2) grant missing:
  `SecurityException ... ACTION_OPEN_DOCUMENT`, also reads as empty; (3) healthy. Without a
  folder-identity log line, tiers 1 and 2 look like an empty folder.
- **F7 crash loop (fixed 2026-10-30):** a pending publish against a tier-1 folder throws
  `IOException: cannot create directory todo/{gid}` from `SafFileStore.write` (via `rename`)
  ~10 s after every app start, FATAL. **Not fixed.**
- **U2 docIds:** stable per folder × provider × device; grants and tree URIs can't be copied
  between devices, the only repair is a real picker pick on that device. On the Nord, `c900964f`
  is `msuite` (its earlier "0 files" was F6 tier 1).
- **U4 two data trees:** picking the root and later `msuite` left `Dropbox/todo/` and
  `Dropbox/msuite/todo/`; wiped 2026-10-28 22:50.
- **Clock skew:** the Nord's time zone is `Asia/Dubai` with the local time set to match CET, so
  its UTC clock is ~3 h behind. HLC copes, but the Nord's edits lose LWW against Moto edits from
  the previous 3 h. Device config, fixed 2026-10-28 (automatic time and zone).
- **Nord time zone:** with automatic time zone on, the Nord picks `Asia/Dubai` (network country
  `ch`) after a reboot. Automatic time keeps its UTC clock right (0.25 s off), so HLC and sync are
  unaffected; only displayed times are off. Set the zone by hand if it matters.
- **T2 results:** round 1 (simultaneous) and round 2 (staggered 1 / 5 / 10 s) both converged,
  no crashes, no `_N` copies; only the writer-made `(1)`s above.
- **T3 result (2026-10-30 22:10), folder-creation race with a dedicated parent:** the Moto
  created `todo/{gid}` and published at 22:10:05.3. At 22:10:06.2 the Nord's
  `resolveOrCreate` found no folder (its view was behind), `createDirectory` returned `null`,
  and the `IOException("cannot create directory ...")` escaped `LiveSync.publish()` on
  `Dispatchers.Main.immediate`: **F7 on a healthy folder, the app closed.** Relaunched at
  22:11:37, the Nord found the folder, published into it, and both converged on 2 files. No
  twin `{gid}_<n>` seen by either device, so with a dedicated parent F1 turns into F7.
- **T3 re-run (2026-10-30 22:32), with the F7 fix:** no crash; the Nord's publish failed
  (caught), and the retry 4 s later *created the folder again* (its view still lacked the Moto's),
  wrote into it and moved the watermark on. Dropbox renamed that folder to
  `{gid}_2805106065466311729`: **F1 is real with a dedicated parent too, and the batch was
  silently lost** (never listed, never resent). Fixed by twin adoption (`6c886c9`) and
  confirm-before-watermark (`e7c15d7`, decision 0077); after installing, both devices adopted the
  twin at 22:50 and converged (76 rows identical, all four T3 todos on both).

## Next session

1. [x] **Check why the GitHub Actions pipeline failed lately** (`gh run list`, `gh run view
   --log-failed`). Likely suspect: `allTests` was red from `ea2d558` until `891630f`
   (`PairingTest`); confirm and fix whatever else fails. Done 2026-10-30: three runs had failed
   (spotless twice, `PairingTest` once), all already fixed locally; green after the push
   (`41127664821`).
2. [x] **Device prep:** both plugged in, screens on, app in the foreground (sync polls only in
   the foreground). Health check: `FolderPlug: list()` count equal on both (11 at handoff), no
   `(1)` / `_N` names. Dump logcat before anything clears it.
3. [x] **T3, folder-creation race (F1):** force-stop both apps, delete `msuite/todo/{gid}` in
   Dropbox, wait until both devices' views of `msuite/todo/` are empty, start both apps within
   the same second, then check `msuite/todo/` for one `{gid}` or two. If two: serialize creation
   (existence check, settle delay, retry) and adopt or merge a `{gid}_<n>` twin.
4. [x] **Republish full state:** the two T3 deletes dropped the older batches from the folder;
   both devices keep them locally, but a late joiner couldn't get them. Force-stop both, clear
   `plug_watermark`, `ingested_batch`, `unreadable_batch` and `unconfirmed_publish` via `run-as`
   (as in the F9 rollout), relaunch; both publish everything once. Done 2026-10-30 22:55: one
   full-state batch each, both confirmed (`unconfirmed_publish` 0), 76 rows on both; one more
   writer-made `(1)`, swept by the next pull.
4b. [x] **T4, soak:** 10+ minutes of alternating edits (~30 s apart). Verify identical
   `field_state` on both, `unreadable_batch` 0, no conflict copies surviving a pull, no
   `syncedFolderUri` change, no crash. Done 2026-10-30 22:56–23:07 (Marc's mixed edits, incl.
   the same list name created on both before a sync): `field_state` identical cell for cell (98 rows, 23 records),
   `ingested_batch` 22 and `unreadable_batch`, `unconfirmed_publish` 0 on both; same 22 names, no
   surviving copies, no twin, no crash, no failed publish, every key named its writer. Two lists
   both called "t3" stay two records, same order on both: expected.
5. [x] **F7 fix:** a write against an unreachable folder becomes a retriable sync error (skip
   the round, surface it in the sync status), never FATAL. Test-first in `core/sync` with a
   failing `FileStore`. Done 2026-10-30: `1ff6131` (core: caught, `healthy`, retry after the
   next pull) and 582728d (Settings shows "Can't reach the folder", EN + DE).
6. [x] **T1 residual:** reboot and `adb install -r` on the Nord keep the folder healthy (the
   `<queries>` fix). Done 2026-10-30
   23:10–23:14: after `adb reboot` and after `adb install -r`, the Nord listed the same 22 files,
   no Dropbox provider or grant error, no msuite crash.
7. [x] Reset the investigation settings on both devices (done 2026-10-28 23:30: `stay_on` 0,
   again on 2026-10-30 23:15 after a session with stay-on for charging devices;
   Doze enabled). They never helped: stay-on only works while charging, and polling stops when
   the screen goes off anyway.
8. [ ] Close the plan: move what lasts into `architecture.md` / `testing.md`, tick step 8 in
   `v1-todo.md`, delete this plan.

## Later

- **Prevent the `(1)` copies at the root, not just sweep them.** Ideas to test: create with
  content in one provider call (none exists in SAF today, check `DocumentsContract` and
  Dropbox's provider on newer versions); write to a staging name and let a later run rename
  (blocked by F4); a native Dropbox plug over its HTTP API instead of SAF (a new plug, needs a
  decision: FOSS SDK or plain Ktor, app key handling). Marc wants this solved eventually.
- **Echo filter** (see F2): per-plug origin per cell, decide with the relay / multi-plug work.
- **Sealed-bytes cache:** a re-seal uses a fresh salt, so a put that half-landed and is retried
  leaves a duplicate under a new key. Cache the sealed bytes per delta; needs a safe "same delta"
  check, as a peer cell can land inside the same HLC range.
- **F5:** don't prune when `list()` returns empty but the plug holds ingested rows; or prune only
  after a confirmed healthy listing.
- **F6 folder-identity log:** log the tree root's display name and child count on sync start,
  and surface tiers 1 and 2 in the UI instead of an empty folder.
- **Test gap:** a `FolderSyncActions`-level test that the folder path stores only ciphertext.
- UX (2026-10-27): the camera forces landscape while pairing; ~10 s wait after picking a folder
  before the first sync (sync right after selection).

## Device state at handoff (2026-10-28 23:15)

- Both run `3eb5dac` (debug). Prefs: Nord `c900964f`, Moto `a1d3faef`, both `msuite`. Both list
  the same 11 files, `field_state` identical (60 rows at 22:58), `unreadable_batch` 0.
- Group `IQEPVMM8EU8S8OHP84ADCE77N8`; device ids Nord `TDG38GH04SLS2`, Moto `UN28KG7BECNL2`.
- adb: wireless, transport ids change on reconnect (last: Nord 3, Moto 2); check
  `adb devices -l` first. Screens lock and doze when unplugged; adb can't unlock (PIN).
- Stay-on and Doze are back to defaults; the Nord's clock is on automatic time and zone (2026-10-28).
- Dropbox app v494.2.2 on both.
- DB backups before the F9 rollout: `%TEMP%\f9\t{2,3}-todo.db`.

## Environment notes (Windows)

- Builds now run on Windows. Earlier device builds came from WSL with a different debug key, so
  the Windows `~/.android/debug.keystore` was replaced with WSL's (old one:
  `debug.keystore.windows-backup`); `install -r` keeps app data.
- Kotlin incremental compilation failed on caches holding WSL paths (`/mnt/c/...`). Use
  `-Pkotlin.incremental=false` (or one `--rerun-tasks` build) until the caches are clean.
- Python is not installed on Windows; read pulled DBs with WSL:
  `MSYS_NO_PATHCONV=1 wsl.exe -e python3 -I /mnt/c/.../script.py`. Pull a DB with
  `adb exec-out run-as com.msuite.todo cat databases/todo.db > file` (journal mode `delete`;
  check `todo.db-journal` is empty first).

## Cheat sheet (log signatures)

- `FolderPlug: list() dir=..., found N files: [...]` — the health check; compare count and names
  across devices. 0 on one device while the peer is healthy = F6 tier 1 or 2, expect F5 pruning.
- `SafFileStore: write() CLOUD path=...` — one per published key; the key's device field must be
  the local device (F2 check).
- `E ActivityThread: Failed to find provider info for com.dropbox...` — F6 tier 1.
- `SecurityException ... ACTION_OPEN_DOCUMENT` — F6 tier 2.
- `logcat -b crash` — F7-style fatals.
- `unreadable_batch` rows — objects that failed to open (foreign root, torn, tampered).
- `plug_watermark` URI vs `syncedFolderUri` in prefs — diverging means the folder changed.
- **Never clear logcat without dumping it first.**
