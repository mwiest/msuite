# 0067: A batch from a newer data-model version is held back, not merged

Date: 2026-10-16. Status: accepted. Fills the open question left by 0006; extends the clamp of 0040.

## Context

Devices run different app versions and share one store. Decision 0006 upcasts older batches and
keeps unknown fields opaquely, but it left undefined what `ingest` does with a batch from a *newer*
version, and the code ignored the version header. A version bump means more than added fields: an
existing field's meaning can change, so merging a batch this app can't interpret could put wrong
values into state.

## Decision

`ingest` reports a batch whose `dataModelVersion` is above this app's as `IngestResult.TooNew`: a
distinct non-merged result, like `Deferred`. The batch is not marked ingested, so it is retried
after an app update; the clock is left alone.

## Rejected

- Merge it anyway, relying on opaque retention: unknown *fields* are safe, but a changed field's
  semantics are not, and the change is silent.
- Mark it ingested and log: a permanently old device would drop real data without a retry.

## Consequences

A device that falls behind on versions stalls on newer batches (and can say why) until it updates,
rather than corrupting state. `TooNew` is a durable signal a later UI can surface. An older batch
(`dataModelVersion` below current) still goes through the identity `upcast` for now (0006).
