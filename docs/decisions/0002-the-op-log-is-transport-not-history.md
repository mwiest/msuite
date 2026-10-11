# 0002: The op-log is transport, not history; snapshots bound remote growth

Date: 2026-07-05. Status: accepted. Formerly OQ-2.

## Context

An op-log grows forever unless something compacts it, and compacting a replicated log usually
needs to know which devices have seen what (membership, cursors). A dumb store cannot track that.

## Decision

Merge is a pure function of per-field state `(value, HLC, deviceId)`, so it is convergent and the
log is only transport: a synced batch collapses into state (higher HLC wins per field).

- Batches are immutable, content-addressed and typed (`delta` | `snapshot`); names are in 0003.
- Deletes are tombstones: `deleted` is an ordinary LWW field. A lower-HLC edit can't resurrect a
  record. Removing tombstones for good is deferred.
- Remote growth is bounded by periodic encrypted full-state snapshots. A snapshot subsumes the
  batches it folds, so they can be pruned without a membership list: a long-offline device merges
  `local ⊕ latest snapshot` and is still correct. No device cursors or high-water marks.
- v1 builds none of the pruning and keeps everything. Only the door-openers ship (immutable typed
  batches, per-field HLC in state).

## Rejected

- Storing only final state without per-field metadata: forecloses any smarter merge later.
- Device cursors / high-water marks: a dead device's stale cursor blocks GC forever.
- Replayed move-ops for reorder: non-convergent, would bring back membership-dependent GC (0004).

## Consequences

No distributed GC as long as merge stays convergent. Non-convergent data (counters, true
co-editing) would bring membership-dependent GC back; that sits behind the CRDT door (0005).
