# 0034: v1 starts with the pure merge core

Date: 2026-10-11. Status: accepted. Replaces 0015.

## Context

0015 kept the original order: storage, then the single-device UI, then HLC, then merge. But the
storage holds `(value, HLC, deviceId)` before any HLC exists, and the UI would be built on a write
path that the merge steps then rewrite. The riskiest part is also the cheapest to build: HLC,
`keyBetween` and the LWW reducer are pure Kotlin, testable without a database or Android.

## Decision

v1 stays infra-first and minimal (the reasoning of 0015 holds: the shared layer is the
deliverable; only door-openers, no deferred machinery). The order becomes:

1. Toolchain upgrade and the app's KMP module (0037).
2. Merge core: stable IDs, HLC with the drift clamp (0040), `keyBetween`, the generic field state
   (0035), LWW merge, the encoder, and simulator Layer 1 as its gate.
3. Storage: SQLDelight over the generic field state.
4. The single-device ToDo app.
5. Plug interface and the manual export/import plug; simulator Layer 2.
6. E2E encryption; simulator Layer 3.
7. QR pairing (0033) and the recovery key.
8. Relay with token access (0030) and the relay plug.
9. Cloud deploy template for the relay.

The simulator layers move with their steps (0007).

## Rejected

- The original order: builds storage and UI against a write path that doesn't exist yet.

## Consequences

The first real code is tested logic, not scaffolding. The first usable app comes one step later.
The current status of each step is in `docs/plans/v1-todo.md`.
