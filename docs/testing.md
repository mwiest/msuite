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
into `core/model` and `core/sync`.

Work is test-first: each feature or change gets at least one test, or a stated reason why none
applies.

## Merge/HLC simulator

Planned for v1 step 2 (decisions 0007, 0034). A seeded, in-memory simulation of N devices:

- each device has a controllable clock (offset, drift, jumps mid-run);
- the transport can partition, reorder, duplicate and drop batches;
- a generated schedule of `Op` / `Tick` / `Push` / `Pull` / `Deliver` steps runs, then a quiescence
  phase connects everyone and syncs to a fixpoint;
- property-based with shrinking (kotest-property), plus named regression scenarios;
- everything is seeded, including the `keyBetween` jitter RNG and the clocks.

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
    merged once that clock catches up; no device's HLC runs more than 24 h ahead of its wall
    clock.

ToDo rules (decisions 0031, 0032, 0033):
14. A task whose list is deleted is hidden on every device, whatever the merge order; a later move
    to a live list makes it visible again.
15. Devices that each created the default list on first run end with exactly one.
16. A device that joins with existing data keeps all of it, and the group converges to the union.

Envelope (decision 0029, Layer 3):
17. A batch renamed or moved to another key fails to decrypt and is not merged.

### Layers

| Layer | Lands with | Covers |
|---|---|---|
| 1 | v1 step 2, gating the LWW merge | pure merge and HLC, invariants 1 to 11 and 13 |
| 1 + ToDo rules | v1 step 4, with the ToDo app's projection | invariants 14 to 16 |
| 2 | v1 step 5 (manual plug), step 8 (synced-folder plug on plain files) | the real plug wrapped in; invariant 12 with the folder plug |
| 3 | v1 step 6 | encrypted batches still converge, invariant 17 |

## Formatting and static analysis

Spotless (ktlint) and detekt (decision 0026). CI runs them with the tests (decision 0025); the
commands are in `AGENTS.md`.
