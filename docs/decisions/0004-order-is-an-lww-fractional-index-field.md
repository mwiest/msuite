# 0004: Manual order is one LWW fractional-index position field

Date: 2026-07-05. Status: accepted. Formerly OQ-4.

## Context

v1 lets the user reorder lists and tasks. Reorder as replayed move-ops is not convergent and would
break the state-based retention of 0002.

## Decision

Each item carries `position`, an LWW fractional-index string (standard `keyBetween` scheme). A list
renders sorted by `(position, itemId)`.

- Append: `keyBetween(lastKey, null)`. Move X between A and B: one write of
  `X.position = keyBetween(A.position, B.position)` with a fresh HLC.
- Generated keys get a short random suffix (jitter), so concurrent inserts into the same gap get
  distinct keys. The RNG is injectable, so the simulator (0007) stays deterministic.
- Equal keys fall back to `itemId`, giving the same total order on every device.
- v1: manual order only, no sort modes. Key rebalancing is deferred; when needed it is a batch of
  ordinary `position` writes.

## Rejected

- Move-ops replayed from the log: non-convergent, needs membership-aware GC.

## Consequences

Merge stays convergent; snapshots still subsume deltas. The remaining anomalies are cosmetic and
are exactly what the simulator asserts: a concurrent move of the same item loses one move
(per-field LWW, 0005); concurrent moves of different items into overlapping ranges interleave
deterministically but maybe surprisingly. Items are never duplicated or lost. Repeated same-gap
inserts grow the key length.
