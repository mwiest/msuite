# 0036: No local op-log; outgoing batches are built from state

Date: 2026-10-11. Status: accepted.

## Context

Merge is state-based (0002), so ops in a local log are redundant with `field_state` (0035). A log
table would be extra schema and code that v1 may not need.

## Decision

No local op-log table until something clearly needs one. An outgoing delta is "every cell whose
HLC is newer than the last one pushed to this plug", using a local watermark per plug. The
no-cursor rule of 0002 is about the shared store, not local bookkeeping. The device also keeps the
set of batch names it has already ingested, for list-and-diff (0003).

## Rejected

- A local op-log table from the start: duplicates state, and is only needed if some feature wants
  per-edit history.

## Consequences

Less schema, one source of truth. Several edits to one field between pushes travel as one value.
A cell merged in from a plug keeps its original HLC, which may be below another plug's watermark,
so with two plugs it wouldn't reach the second one. v1 has one plug; relaying between plugs needs
per-plug tracking when it comes.
