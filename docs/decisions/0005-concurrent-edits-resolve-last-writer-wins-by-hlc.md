# 0005: Concurrent edits resolve last-writer-wins by HLC; the loss is accepted

Date: 2026-07-05. Status: accepted. Formerly OQ-5.

## Context

Devices sync rarely, so most conflicts are between concurrent edits (neither device saw the
other). No timestamp scheme can order those correctly; only version vectors or a non-LWW merge
can even detect them.

## Decision

Per-field last-writer-wins on a Hybrid Logical Clock, behind one `merge(local, remote)` interface.

- HLC gives causality (if A was visible when B was written, `HLC(A) < HLC(B)`), stays close to
  wall-clock time, and `(pt, c, deviceId)` is a deterministic total order, so every device picks
  the same winner.
- Concurrent edits to the same field: the higher HLC wins and the other value is silently lost.
  Accepted for v1; no ToDo field is costly to lose.
- Pure HLC, no max-drift clamp: a clock set far in the future wins permanently. Accepted for v1.

## Rejected

- Conflict detection with per-field version vectors: per-device metadata and dead-device pruning
  would undo 0002's membership-free retention.
- CRDTs now: only needed for true co-editing (e.g. rich text) and would need a Rust engine, against
  "stay in Kotlin". Deferred, not rejected.

## Consequences

Simple, pure Kotlin. A fast-clock device wins concurrent conflicts; the simulator treats that as
expected, and divergence as the bug. Revisit when:

- a bad clock shows up in the wild: add the clamp (reject batches whose `pt` is more than about
  1 h to 1 d ahead of local `now()`) at ingest, no format change;
- app #2 has a field where loss is unacceptable (a notes body): give that field its own merge
  strategy (CRDT or 3-way) behind `merge()`.
