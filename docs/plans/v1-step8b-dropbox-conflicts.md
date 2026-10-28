# Plan: v1 step 8b, Dropbox conflict-file investigation

Status: **In progress** (2026-10-28). The 2026-10-27 session defined the problem; the
2026-10-28 session root-caused U1/U2 with traces and found three new issues (F6–F8).
Remaining work: repair the Nord (manual folder re-pick), run T2–T4 on healthy devices,
then implement the fixes. See [Current device state](#current-device-state-at-handoff-2026-10-28-evening).## Context

Step 8's milestone verification on two real devices (OnePlus Nord AC2003, Motorola edge 70 pro,
Android 16, Dropbox as the synced-folder backend) surfaced several issues around conflict files
and folder-URI stability. Some are understood, some are not. This plan captures everything so
we can start fresh with good instrumentation.

## What was verified working (2026-10-27)

1. Pairing by QR on both devices.
2. Fresh start (`pm clear` + re-pick) works.
3. Sync through a shared Dropbox folder: both devices converge (field_state 7 → 15 → 28 rows,
   identical record IDs on both).
4. Bidirectional sync: Nord→Motorola and Motorola→Nord both deliver edits.
5. Offline edits: created offline, synced after reconnecting.
6. Ciphertext only in the folder: batch files are binary, no plaintext found.
7. Key grammar matches decision 0029: `todo/{groupId}/v1_d_{deviceId}_{hlcLo}_{hlcHi}_{hash}`.
8. **A single-device write does NOT create `(1)` conflict files** (proven by TRACE, see F3).

## Device IDs and paths used throughout

- Group id: `IQEPVMM8EU8S8OHP84ADCE77N8`.
- Nord device id `TDG38GH04SLS2`, Moto `UN28KG7BECNL2`.
- Keys: `todo/{groupId}/v1_d_{deviceId}_{hlcLo}_{hlcHi}_{hash}`.
- adb: Windows via WSL (`adb.exe`); transport IDs change across reconnects — verify with
  `adb.exe devices -l` first (last seen: Nord 3, Moto 2).
- App DB access: `run-as com.msuite.todo`; read DBs with `python3 sqlite3` (no sqlite3 CLI).

## Findings from 2026-10-27 (understood)

### F1: Two group folders created when both devices race to mkdir

When both devices picked the *root of Dropbox* and started sync at the same time, both tried
to create `todo/{groupId}/`. Dropbox created two folders: `{groupId}` and
`{groupId}_840897305336667523` (conflict suffix). Each device then wrote only into its own
folder — sync silently broke.

**Workaround used**: delete everything, create a dedicated parent folder (`msuite`) in Dropbox,
pick that folder on both devices. Then only the `{groupId}` subfolder gets created once.

**Open question**: is folder creation itself racy even with a dedicated parent? Not retested (T3).

### F2: Both devices published the same object key (device_id duplication)

After the fresh start, both devices' `field_state` rows carried the Motorola's `device_id`
(-737312427549184175 → `UN28KG7BECNL2`), even on the Nord. Both devices then derived identical
object keys (`v1_d_UN28KG7BECNL2_06GNUDO884000_...`), and Dropbox created a
`_5110404403298289004`-suffixed conflict copy **with real data** (150 bytes).

Mechanism: both devices created the same default list (same `record_id FE8696E34E7F4D53`)
independently after pairing; the Nord ingested the Motorola's batch, whose cells won the merge
(higher HLC), so the Nord's own stamps were overwritten in `field_state`. The Nord's next
delta then contained cells stamped with the Motorola's device_id → same key as the Motorola.

Root-cause site: `LiveSync.publish()` takes `deviceId = delta.cells.first().stamp.deviceId`
(the peer's stamp) instead of the local device id
(`core/sync/.../LiveSync.kt`). **Root fix not implemented.**

Recent recurrence: the Nord sat on a pending 1257-byte delta (stamped
`v1_d_UN28KG7BECNL2_...`) that finally published at 21:31 on 2026-10-28 — into the wrong,
stale tree (see U4 / folder map). Re-publishing that existing key also produced an
`_992501984378458056` conflict copy (F8).

**Workaround used**: create fresh data on the Nord so it publishes its own device_id.

### F3: `(1)` conflict files are 0 bytes and are NOT created by a single write

Instrumented `SafFileStore.write()` with TRACE at five steps plus a FLAGS probe. A
single-device write never produced a conflict at any step (before / after-delete /
after-create / after-write / after-3s-delay). Every `(1)` file in the folder is 0 bytes; its
original has content.

**Conclusion**: the SAF write path (delete → create → openOutputStream → write) does not
itself trigger Dropbox conflict copies. The `(1)` files must come from cross-device sync or
Dropbox's engine reacting to two devices' state. Two-device simultaneous-write experiment
(T2) is needed to prove it. (Update 2026-10-28: F8 shows a *re-publish of the same key* does
produce a conflict — at createFile time, as a `_N` copy with data, not a 0-byte `(1)`. T2
still decides the `(1)` question.)

### F4: Dropbox's SAF flags contradict reality

`FLAGS[before-write]` on real Dropbox files reports `raw=324`:

- `rename=true` — but decision 0075 proved `renameTo()` throws
  `FileNotFoundException: invalid path`. The flag lies.
- `copy=false` — `copyDocument()` genuinely unsupported.
- `delete=true`, `move=true`.

So there is no rename- or copy-based atomic-publish workaround on Dropbox. Decision 0075's
local-temp approach stands.

### F5: ingested_batch pruning wipes bookkeeping when the folder goes empty

When the Nord's folder view broke (F6 tier 1) and `list()` returned 0 files, the pruning logic
removed **all** `ingested_batch` rows (they're "no longer in list"). `field_state` survived,
so the UI still showed all todos — but the device had no record of what it had ingested.
On recovery it must re-read everything; correctness held (create-only keys, idempotent
ingest), but this interaction turns any silent folder failure into a full re-scan.

## Resolved on 2026-10-28

### U1 (root-caused and fixed): the Nord's folder access died from package-visibility loss

Yesterday's symptom: the Nord's `syncedFolderUri` appeared to "revert" to the pre-clear value
`c900964f` and `list()` returned 0 files. Two parts of that were misdiagnoses:

- **A real re-pick happened at 23:52** (grant re-taken proves it) — the pref-changed feeling
  of mystery was real, but the newly picked value later drifting to `c900964f` was **not**
  the root cause; grants and prefs had stayed intact.
- **Root cause**: the OnePlus (Android 16) build dropped the app's grant-derived package
  visibility of `com.dropbox.android`. `getPackageInfo` → `NameNotFoundException`,
  `resolveContentProvider` → null, and `E ActivityThread: Failed to find provider info for
  com.dropbox.product.android.dbapp.document_provider.documents` on every poll. All SAF
  queries silently returned empty folders while prefs and grants looked perfect. H-b
  (revoked grants) refuted; H-a "URI instability" refuted; docIds are stable per folder
  and provider.

**Fix (kept, validated on the Nord without re-picking)**: declare the provider in
`AndroidManifest.xml`:

```xml
<queries>
    <intent>
        <action android:name="android.content.action.DOCUMENTS_PROVIDER" />
    </intent>
</queries>
```

First attempt used `android.intent.action.OPEN_DOCUMENT_TREE` and failed — that intent is
served by the system picker, not by documents providers. What *triggered* the loss of
grant-derived visibility is not pinned (suspect: provider/app restart path on the OnePlus
build); the `<queries>` declaration makes it robust regardless. Decision record pending.

### U2 (root-caused): URI → folder map

TREE instrumentation (`TREE[dir=...] treeDocId=... rootName=...`) resolved every docId:

| docId | On device | Dropbox folder | State |
|---|---|---|---|
| `b7422704` | Moto | account root (`Privat`) | the step-3 re-pick — **picked ROOT, not msuite** (yesterday's note that it was msuite was wrong) |
| `a1d3faef` | Moto | `msuite` | healthy — holds the live tree, 13 files |
| `ed1a838b` | Nord | account root | Nord's current pref — **wrong folder**, data lives under `msuite` |
| `c900964f` | Nord | original pre-clean root pick | dead path (data moved to `msuite`), 0 files |
| `a1d3faef` (Moto's id) written by hand to Nord | Nord | (same folder, different device) | `SecurityException` — no grant; **grants and docIds are per-device**, cannot be copied |

So: docIds are stable per folder × provider × device; trees valid on one device are worthless
on another; the repair is always a real picker pick on that device. The provider serves
`children` queries in the form
`content://com.dropbox.product.android.dbapp.document_provider.documents/tree/<docId>/document/<docId>/children`.

### U3 (partially answered): when are conflicts created?

Proven on 2026-10-28: a **single-device re-publish of a key whose server copy is not yet
visible to SAF** produced a Dropbox conflict copy **at createFile time** — `createFile()`
returned a RENAMED document, suffixed `_992501984378458056` (with real data). That means:

- Dropbox conflict copies can appear even with no second device actually writing, whenever
  the writer's view is behind the folder's server state (eventual consistency).
- They appear as `name_<n>` **and** as `name (n)`; see F8 for what our cleanup catches.
- What is still unproven: whether two devices writing *different* keys simultaneously (T2)
  produce 0-byte `(1)` files (F3 suspects so), and the race window.

### U4 (resolved): the lost Nord ingest + two parallel data trees

The overnight mystery was not one folder changing under us but **two parallel data trees**
in the Dropbox account:

```
Dropbox/
├── todo/{gid}/              ← STALE: 1 file (Nord's late F2-stamped delta publish + _9925... copy)
└── msuite/
    └── todo/{gid}/          ← LIVE: 13 files (+ yesterday's residue, see F8)
```

Yesterday's "cleanup" emptied `Dropbox/todo/{gid}` but `root/todo/{gid}` — the tree both
devices had been pointed at when picking ROOT — kept existing with one file. The Nord's
pending delta finally published there at 21:31, opportunistically, when its folder briefly
worked again. The Nord lost nothing; the data it "ingested with the wrong folder" question
is moot. Lesson: never clear logcat before dumping it (that is how the 21:05/21:18 evidence
was almost lost; today's U4-adjacent crash-loop evidence was captured because we dumped
first).

## New findings from 2026-10-28

### F6: the three failure tiers of a Dropbox SAF folder (all silent)

| Tier | Trigger | What surfaces | What the app sees |
|---|---|---|---|
| 1 | provider invisible (U1) | `E ActivityThread: Failed to find provider info ...` per poll | queries succeed; folder reads as empty (`DocumentFile` swallows exceptions) → F5 pruning kicks in |
| 2 | provider visible, URI grant missing | `E DatabaseUtils: SecurityException: ... requires that you obtain access using ACTION_OPEN_DOCUMENT or related APIs` | also reads as empty, same silent path |
| 3 | healthy | — | files visible |

Health checks that distinguish them (see cheat sheet): `TREE[dir=...]` line
(`rootName="<SECURITY-EXCEPTION: grant dead>"` = tier 2, `rootName="null" children=[]` = tier 1)
and the `DROPBOX-ACCESS` trace (visibility + persisted grants). Without instrumentation,
**a tier-1 or tier-2 failure is indistinguishable from an empty folder** — that was yesterday's
trap.

### F7: crash loop when a pending publish meets a dead folder

When the Nord had a pending delta and its folder was in tier 1, on every app start
(~10 s in, `Dispatchers.Main.immediate` from the debounce collect):

```
java.io.IOException: cannot create directory todo/IQEPVMM8EU8S8OHP84ADCE77N8
    at SafFileStore$write$2.invokeSuspend(SafFileStore.kt:121)
    at SafFileStore$rename$2.invokeSuspend(SafFileStore.kt:275)
    → FATAL
```

Observed twice on 2026-10-28 (21:05:46, 21:18:35). The pending publish cannot land until the
folder is repaired, and each app start re-runs it into the same fatal. Fix direction: the
`resolveOrCreate`/`write` path must treat "provider unreachable / no such directory" as a
retriable sync error (degrade to skip this round + report), never an app-crashing condition.
Not yet fixed.

### F8: Dropbox conflict copies come in `_N` form too, and our cleanup misses them

The cleanup inside `SafFileStore.write()` (lines 152–161) deletes duplicates matching
`^name (\d+)$` only. Both forms exist in the wild:

- `name (1)` etc., 0 bytes (yesterday's three in `msuite/todo/{gid}`, origin still T2's question).
- `name_<digits>` **with real data**, created by Dropbox at createFile time when the server
  already has that key but the client's SAF view doesn't (U3's `_992501984378458056`;
  F2's `_5110404403298289004`). These are the dangerous ones — they contain a full batch
  copy that will get re-ingested as `unreadable_batch` or duplicate data depending on timing.

Note: an `_N` conflict happens *even on a re-publish that is logically idempotent for us*.
Fix direction (not implemented): broaden the cleanup to also catch `^name_\d+$`, and/or stop
tripping moot re-publishes (F2's publish-side fix shrinks the windows). Not band-aided yet;
needs the T2 data to decide handling of 0-byte `(1)` vs data-bearing `_N`.

## Current device state at handoff (2026-10-28 evening)

Logcat dump for the whole session in `/tmp/opencode/t1/logcat-nord-*.txt` (plus prefs,
grants, DB snapshots, crash logs).

- **Moto**: pref `a1d3faef` (`msuite`, healthy, 13 files), `<queries>` manifest fix installed,
  app force-stopped. This device is ready to run the test plan.
- **Nord**: pref `ed1a838b` (account **root** — the re-picks were of the root, not msuite;
  WRONG for our data), grants `c900964f` + `ed1a838b` persisted, DB converged (28
  `field_state` rows), `<queries>` manifest fix installed, app force-stopped.
  **Cannot sync until Marc re-picks the `msuite` folder in the SAF picker on the Nord**
  (grants can't be teleported; adb can't drive the picker, PIN lock blocks screen control).
  After the re-pick, verify: TREE line says `rootName="msuite" children=[todo]` and
  `list()` = 13 files matching Moto.
- Both: `svc power stayon true` + `dumpsys deviceidle disable` (deep + light) set during
  investigation — **reset at the end** (`svc power stayon false`, `dumpsys deviceidle enable`).
  Note stayon only holds while plugged in; devices were on wireless adb, so plan for screens
  going dark + doze unless plugged.
- Dropbox app v494.2.2 on both, same version, not updated overnight.

## Instrumentation status (stripped, fix committed in bc0ff76)

The debug-only instrumentation (SafFileStore TRACE[1..5]/traceFiles/traceFlags/TRACE delay/
createFile-rename warning/traceRoot TREE lines, MainActivity `traceDropboxAccess()`) was
stripped and the kept changes committed as bc0ff76 (2026-10-28):

- AndroidManifest `<queries>` fix (decision 0076) — kept, validated on-device.
- FolderPlug `list()` single-log simplification — kept.
- kermit dependency in `core/sync/build.gradle.kts` — kept.

The TREE idea was the single most useful diagnostic of the whole investigation — if F6
tier 1/2 can ever recur, a production-safe "folder identity" log line (root display name +
child count on sync start) is worth keeping deliberately.

## Test plan (remaining)

Preparation: Marc re-picks `msuite` (NOT root) on the Nord in the SAF picker and confirms
the health check above. Moto is already ready. Both: `adb.logcat -d` and stash a dump before
any logcat change; verify before/after with `FolderPlug: list()` (`list() ... found N files`).

### T1 residual (already root-caused; re-run only the maintenance matrix)

U1/U2 are answered. Remaining value in T1 is regression confirmation, on the *repaired* Nord:

1. Re-run the steps that historically broke access (device reboot scenario 1 in the earlier
   plan; `adb install -r` scenario 5) and confirm the `.queries` fix keeps tier 3 through
   each.
2. Keep scenario 7 as a permanent health check: log the tree root's display name (TREE
   style) on every sync start.

### T2: Two-device simultaneous write (root-cause U3's remaining half)

1. Verify both devices list the same files (same count, same names).
2. Logcat running on both. Create a todo on **both devices within the same second**
   (e.g. "T2-Nord" and "T2-Moto").
3. Wait for debounce (10 s) + publish + settle on both.
4. Check for `createFile() returned RENAMED document` warnings and `CONFLICTS:` lines from
   both sides; then list the folder from either device and diff names against the snapshot.
5. If a conflict appears: was it visible in the writer's final trace (writer-created) or only
   in the reader's next pull (reader/sync-created)? That distinction decides whether
   prevention (write-side) or cleanup (list-side) is the right layer. Expect `_N` copies
   with data (F8) if a re-publish races; 0-byte `(1)` remains the F3 suspect.
6. Repeat with staggered writes (1 s, 5 s, 10 s apart) to find the race window.

### T3: Folder-creation race (root-cause F1's open question)

1. Only after T2/T4 (cleanup decision: observe first): delete `{gid}` from `msuite/todo/`,
   both apps force-stopped; both devices' view of the parent must be empty first.
2. Start both apps within the same second → both try to create `todo/{gid}/`.
3. Check the parent: one `{gid}` or two (`_<n>` conflict)? If two again, the fix must
   serialize folder creation (retry-with-existence-check + settle delay).
   Note: today `msuite/todo/` holds exactly one `{gid}`, so T3 starts from a clean race.

### T4: Long-run soak (confidence)

10+ minutes of alternating edits on both devices (~30 s apart), then verify: identical
`field_state` row counts and record IDs on both; no new conflict files, or if present they
get cleaned by a *broadened* cleanup (F8); `ingested_batch` counts stable; TREE lines stable;
no `syncedFolderUri` changes in prefs.

## Cleanup candidates (decided 2026-10-28: observe first)

Delete nothing yet — leave the residue in Dropbox and let T2/T4 observe whether the current
cleanup and ingest handle it as expected. Delete afterwards if those tests don't already
tell us enough:

1. Stale `Dropbox/todo/` tree (Nord's late publish + `_9925...` copy).
2. Old residue in `msuite/todo/{gid}`: three 0-byte `(1)` files and the `_51104...` copy.

## Cheat sheet (log signatures)

- `TREE[dir=...] treeDocId=... rootName=...` — folder-identity health check; `rootName=null`
  = F6 tier 1, `<SECURITY-EXCEPTION: grant dead>` = tier 2.
- `DROPBOX-ACCESS DONE` — provider visibility + persisted grants snapshot.
- `TRACE[1-before]` … `TRACE[5-after-3s-delay]`, `CONFLICTS:` lines — per-write conflict
  snapshots (currently instrumentation-only).
- `createFile() returned RENAMED document` — provider renamed on create; watch the actual
  name: `_N`-form conflicts (F8) carry real data.
- `FLAGS[...]` — which operations the provider claims to support (remember: rename lies,
  F4).
- `FolderPlug: list() ... found N files` — fastest health check; compare across devices.
  0 on either device + healthy TREE on the peer = silent folder failure, expect F5 pruning.
- `LiveSync: publish() writing key ...` — key being written; field 3 (`v1_d_XXXX...`) must
  be the local device's id, not the peer's (F2 recurrence check).
- `-b crash` buffer for F7-style fatals; trace filter for the rest: `logcat -d -s
  SafFileStore:D FolderPlug:I`.
- **Never clear logcat without dumping it first** (U4's lesson).
- `unreadable_batch` rows appearing — a sign 0-byte conflict files (or cross-version
  garbage) were ingested.
- `plug_watermark` URIs in the DB vs `syncedFolderUri` in prefs — if they diverge, the
  folder config changed under us.

## Follow-ups

- Root-fix F2: deltas must be annotated with the *local* device id at publish, or field_state
  must never let peer stamps masquerade as local unpushed changes (`LiveSync.publish()`).
- Root-fix U1 for all Android 16 (and possibly earlier) builds: `.queries` declaration +
  decision record; note in `architecture.md`.
- Root-fix F7: publish/write against an unreachable provider degrades to retriable silent
  skip, never FATAL.
- Root-finish F8 (only after T2 decides): broaden the duplicate cleanup to `_N` form, choose
  handling for data-bearing vs 0-byte copies, and consider a startup list-sweep.
- Repair instructions are Marc's manual step: re-pick `msuite` on the Nord, run the health
  check, then choose cleanup items above.
- Decision 0075: add the F4 flag-lies line (`rename=true` while `renameTo()` throws).
- UX items (from 2026-10-27): camera forces landscape during pairing; ~10 s wait after
  picking a folder before first sync — trigger an immediate sync after folder selection.
