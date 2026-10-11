# 0007: Merge is gated by a seeded, property-based multi-device simulator

Date: 2026-07-05. Status: accepted. Formerly OQ-7.

## Context

Merge and clock bugs show up only under specific interleavings, clock skews and network faults,
which hand-written tests rarely hit.

## Decision

A seeded in-memory simulator of N virtual devices with controllable clocks (offset, drift, jumps),
exchanging batches over a transport that can partition, reorder, duplicate and drop. A generated
schedule runs, then a quiescence phase syncs everyone to a fixpoint, then the invariants are
checked. Property-based with shrinking (kotest-property in `commonTest`, run on `jvm`), plus named
regression scenarios. Everything is seeded, including the `keyBetween` jitter (0004).

It grows in layers: Layer 1 (pure merge/HLC) gates the LWW reducer at v1 step 4, Layer 2 wraps the
real plug at step 5, Layer 3 runs through encryption at step 6. The invariants are listed in
`docs/testing.md`.

## Rejected

- Example-based tests only: they don't explore interleavings.

## Consequences

The harness asserts convergence and bounded loss, not real-time correctness. Every source of
randomness and time in `core/sync` has to be injectable.
