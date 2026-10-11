# 0006: Schema changes use stable IDs, read-time upcasters and opaque unknown fields

Date: 2026-07-05. Status: accepted. Formerly OQ-6.

## Context

Devices run different app versions. Because the log is not history (0002), there is no long
history to replay; migration means local state plus batches written in an older or newer format.

## Decision

- Fields, ops and record types have stable opaque IDs; names are display-only, so renames cost
  nothing. Removed IDs are reserved forever. A type change is remove-old-ID plus add-new-ID.
- Each batch carries a data-model version inside its encrypted header. Ingest is: decrypt, run a
  pure `upcast(vK -> vK+1)` chain up to the current version, merge. Batches are never rewritten.
- Unknown field IDs are kept opaquely as `(id, bytes, HLC, deviceId)`, carried through merge and
  written into snapshots, so an older app never erases newer fields. Storage: typed columns for
  known fields plus a small side table for unknowns.
- Local state migration is ordinary versioned SQLDelight migration.
- v1 builds only the seams: stable IDs in the encoding, opaque retention, version header = 1, and
  an identity `upcast`.

## Rejected

- Replaying history through migrations: there is no permanent history.
- Relying on protobuf's own unknown-field retention: modelled explicitly instead (0016).

## Consequences

The migration surface is add / remove / type-change, bounded to in-transit deltas and local state.
Rare non-pure migrations are handled case by case.
