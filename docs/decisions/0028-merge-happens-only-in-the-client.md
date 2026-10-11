# 0028: Merge happens only in the client; backends store opaque ciphertext

Date: 2026-07-02. Status: accepted.

## Context

Sync and fault tolerance across devices are wanted without a central server the developer has to
run, and without accounts.

## Decision

- Backends are dumb, interchangeable stores of E2E-encrypted batches. They need no accounts, no app
  logic and no schema, so they can be hosted anywhere, or be a synced folder.
- Sync moves an ordered log of field-level ops (`set field X = Y @ HLC on device D`), not whole
  records.
- All merge logic lives in the client, behind one `merge(local, remote)` interface. Never a second
  merge layer below it (0003).
- Every record carries per-field `(value, HLC, deviceId)`; the data format is versioned.
- Search is local only; E2E rules out server-side indexing.

## Rejected

- A smart server that merges: needs accounts, app logic and hosting by the developer.
- Syncing whole-record state blobs: loses per-field merge.

## Consequences

Any storage the user already has can be a backend (0003). Merge correctness rests entirely on the
client, hence the simulator (0007). Per-field metadata keeps the door open for smarter merges
(0005).
