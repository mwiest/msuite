# Testing

How msuite is tested: kotest on the JVM, and the merge/HLC simulator that gates the sync layer.

## Running tests

```bash
./gradlew allTests               # all KMP targets
./gradlew test                   # JVM and Android unit tests
./gradlew :core:sync:jvmTest     # the sync tests and simulator only
```

Logic lives in `commonMain`; its tests live in `jvmTest` and run with kotest on JUnit 5
(decision 0038). The stack is kotest (runner, assertions, kotest-property). Today kotest is wired
into `core/model`, `core/storage`, `core/sync` and `apps/todo/domain`. Storage tests run on a real SQLite through
sqlite-jdbc, in memory or in a temp file (decision 0046).

Work is test-first: each feature or change gets at least one test, or a stated reason why none
applies.

## Merge/HLC simulator

Decisions 0007, 0034 and 0057. The harness is `Cluster` in `core/sync-testing`: a seeded
simulation of 2 to 4 devices sharing an append-only store of batches, through the real `ingest`,
`buildDelta` and `merge`. Each layer or app extends it with its own write steps and invariants:
Layer 1 is `core/sync/src/jvmTest/.../Simulation.kt`, driven by `SimulatorTest.kt`. The harness
gives every simulator:

- each device has its own wall clock: an offset (one device may be days ahead), uneven ticks, and
  jumps forward or back mid-run;
- a push can fail (retried by a later push), a device can go offline, and a pull fetches a random
  subset of the unseen batches in random order and re-fetches some it has seen;
- a generated schedule of the simulator's write steps and the shared `NetStep`s (`Tick` / `Jump` /
  `Push` / `Pull` / `Toggle`) runs, then a quiescence phase connects everyone, moves every clock
  past the 24 h bound, and syncs to a fixpoint;
- each device keeps its state behind `SimState` (`SimState.kt`): `MemoryState` runs the pure
  functions over a map, `ReplicaState` the real `Replica` over in-memory SQLite, so the storage
  path is checked against the same invariants. The shared store is a `SimStore`: `BatchStore` keeps
  batches in memory (Layer 1), `PlugStore` pushes encoded bytes through a real `BackendPlug`;
- everything is seeded, including the `keyBetween` jitter and the clocks.

Layer 1 writes generic `Write` steps to a few records. It is property-based (kotest-property, 1000
schedules in memory and 300 through `Replica`, shrinking the step list), plus named scenarios: a
faster clock wins a conflict, a causal edit wins on a slower clock, a delete beats an older edit,
a far-future batch is held back and merged later.

Layer 2 runs the same schedules with whole snapshots through a `PlugStore`, in memory and through
`Replica` (`SimulatorTest.kt`), and checks that an object that won't decode is skipped, retried a
bounded number of times, then flagged corrupt and left alone (decisions 0068, 0069). Invariant 12
lands with the folder plug at step 8. Step 8 also adds a delta-publishing mode over `PlugStore`
with the real key grammar of 0029 — today's `sim/<hash>` keys are flat, so spans, types and
pruning are not exercised — plus pruning steps asserting invariants 18 and 19.

Layer 3 runs the schedules again through `EnvelopePlugStore` (code, step 6): the real object keys of
0029, the `hash` over the ciphertext, and the AAD from the key minus its hash (0071), in memory and
through `Replica`. A fetch is `Fetch.Read`, `Fetch.Skip` (a TooNew header or an unknown `keyGen`,
held back without the corrupt flag) or `Fetch.Unreadable` (counted, 0069). Named scenarios cover a
TooNew header held back, an unknown `keyGen` skipped, and a renamed or foreign-root object that
fails to open, is not merged, and is flagged corrupt after the bounded retries (invariant 17).

The ToDo simulator (`apps/todo/domain/src/jvmTest/.../TodoSimulation.kt`, driven by
`TodoSimulatorTest.kt`) runs the real `ListCommands`, `TaskCommands` and `projectTodos()` on
`Replica`s. Every device first runs its first-run setup, alternating English and German, then
picks its targets from its own current view. A raw `Relist` write points a task at another list,
standing in for a later "move to list" feature. It checks invariants 1 and 14 to 16, with 200
schedules plus two named scenarios: a deleted list's task hidden and revived by a move, and a device
that worked offline from the start joining. Invariant 14 is checked against an oracle that
recomputes visibility from the raw cells.

Invariant 7 is property-tested on its own in `core/model` (`FractionalIndexTest`).

### Invariants

Convergence:
1. Byte-identical materialized state on all devices after quiescence.
2. Delivery order doesn't matter.
3. Delivering a batch twice changes nothing.

LWW and clock (decision 0005):
4. Each field's winner is the max `(pt, c, deviceId)`, computed independently and compared.
5. Causally ordered edits (A seen before B was written) resolve to B.
6. Fields are independent; no phantom values.

The harness asserts convergence and bounded loss, not real-time correctness: a fast clock winning
a concurrent conflict is expected; divergence is the bug.

Reorder (decision 0004):
7. `keyBetween` fuzzed: `a < keyBetween(a, b) < b`, append and prepend bounds, concurrent same-gap
   keys stay strictly inside the gap and distinct.
8. No lost or duplicated items; interleaving is the only anomaly.
9. `(position, itemId)` is a strict total order, the same on every device.

Tombstones (decision 0002):
10. A lower-HLC edit never resurrects a deleted record.

Snapshots (decision 0002; the v1 manual plug path):
11. `merge(deltas)` equals `merge(snapshot_of(deltas))`, and `local ⊕ latest snapshot` equals
    `local ⊕ folded deltas`.

Plug contract (decisions 0003, 0029):
12. Concurrent puts give distinct objects, never a clobber; `list` returns only fully published
    keys.

Clock clamp (decision 0040):
13. A batch with `hlcHi.pt` more than 24 h ahead of a device's clock is not merged there, and is
    merged once that clock catches up; merging never moves a device's HLC more than 24 h past the
    latest wall-clock time it has seen. (A wall clock set back can leave the HLC further ahead,
    since an HLC never goes back.)

ToDo rules (decisions 0031, 0032, 0033):
14. A task whose list is deleted is hidden on every device, whatever the merge order; a later move
    to a live list makes it visible again.
15. Devices that each created the default list on first run end with exactly one.
16. A device that joins with existing data keeps all of it, and the group converges to the union.

Envelope (decision 0029, Layer 3):
17. A batch renamed or moved to another key fails to decrypt and is not merged.

Compaction (decision 0002; the spans of 0029; planned with v1 steps 8 and 9):
18. After a writer publishes a snapshot and prunes its earlier objects, every reader converges on
    the surviving objects alone — no reader still needs a pruned object.
19. A device that first syncs only after pruning converges to the same state, and each writer's
    object count stays bounded: a snapshot plus a grace window's worth of deltas.

### Layers

| Layer | Lands with | Covers |
|---|---|---|
| 1 | v1 step 2 (landed), gating the LWW merge | pure merge and HLC, invariants 1 to 11 and 13 |
| 1 + ToDo rules | v1 step 4 (landed) | invariants 14 to 16, through the ToDo commands |
| 2 | v1 step 5 (landed), step 8 (landed: folder plug on plain files, delta mode, pruning) | the real plug wrapped in; invariant 12 with the folder plug, 18 and 19 with pruning |
| 3 | v1 step 6 (landed) | encrypted batches still converge, invariant 17, and the TooNew / unknown-`keyGen` / foreign-root scenarios |

## Two-device check (Android, synced folder)

What the simulator can't cover: a real sync client under the folder. Last run 2026-10-30 on a
OnePlus Nord and a Motorola edge 70 pro (Android 16), Dropbox app v494.2.2.

Setup: pair by QR; on each device pick a **dedicated** folder (e.g. `msuite`), not the account
root. Both phones plugged in (`adb shell svc power stayon true` holds only while charging; set it
back to `false` afterwards) with the app in the foreground: sync polls only while the app is
visible, so a locked screen stops it.

| Check | How | Pass |
|---|---|---|
| Health | compare the latest `FolderPlug: list()` on both | same count and names, no `(N)` / `_<digits>` names left |
| Simultaneous and staggered writes | a task on each device in the same second, then 1 / 5 / 10 s apart | both converge, no crash |
| Folder-creation race | delete `{appId}/{groupId}` in the cloud, wait for both lists to read 0, write on both in the same second | no crash, no twin folder after a pull, both converge |
| Soak | 10+ minutes of mixed edits, alternating, sometimes the same task on both | `field_state` identical cell for cell; `unreadable_batch` and `unconfirmed_publish` empty |
| Restart | `adb reboot`, unlock, open; then `adb install -r` | same list, no provider or grant error |

Compare databases cell for cell: `adb exec-out run-as com.msuite.todo cat databases/todo.db >
file` (journal mode `delete`: check `todo.db-journal` is empty first), then read both copies with
Python's `sqlite3`. Deleting the group folder drops the published batches, and live sync doesn't
refill it yet (see `roadmap.md`): force-stop both apps, clear `plug_watermark`, `ingested_batch`,
`unreadable_batch` and `unconfirmed_publish` in a copy, push it back with `adb exec-in run-as ...
sh -c 'cat > databases/todo.db'`, relaunch.

Log signatures (`adb logcat -d`; dump before anything clears the buffer, it also rotates within
minutes):

- `FolderPlug: list() dir=..., found N files: [...]`: one per pull; the health check.
- `SafFileStore: write() CLOUD path=...`: one per publish; the key's device field must be this
  device's.
- `FolderPlug: adopted twin folder ...`, `LiveSync: publish failed, retrying later`: the race and
  an unreachable folder, both recovered.
- `E ActivityThread: Failed to find provider info for com.dropbox...`: the provider is invisible
  (decision 0076); `SecurityException ... ACTION_OPEN_DOCUMENT`: the grant is gone, re-pick. Either
  reads as an empty folder.
- `adb logcat -d -b crash`: fatals; check the process name, other apps crash too.

Gotchas: a device's UTC clock matters, its time zone doesn't (an automatic time zone can be wrong
while automatic time is right); adb can't unlock a PIN screen or drive the folder picker; a debug
build signed with another machine's debug key won't `install -r` over the old one.

## Formatting and static analysis

Spotless (ktlint) and detekt (decision 0026). CI runs them with the tests (decision 0025); the
commands are in `AGENTS.md`.
