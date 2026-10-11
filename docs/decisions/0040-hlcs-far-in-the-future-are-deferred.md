# 0040: Batches with HLCs far in the future are deferred at ingest

Date: 2026-10-11. Status: accepted. Replaces "pure HLC, no max-drift clamp" in 0005.

## Context

Under pure HLC, one device with a clock set far in the future (a wrong year) wins every field it
touches forever, and every device that merges its batches inherits the future time in its own HLC.
Fixing it later needs every device to update first.

## Decision

- Ingest defers a batch whose `hlcHi.pt` is more than 24 hours ahead of the local clock: it is not
  merged and not marked as ingested, so list-and-diff retries it on later syncs and it merges once
  the local clock is within the bound.
- The local HLC never advances more than 24 hours beyond the local wall clock.
- The bound is generous because it only has to catch broken clocks, not ordinary skew.

## Rejected

- Rejecting such batches for good: loses that device's edits.
- No clamp (0005 as written): a single bad clock poisons the group permanently.

## Consequences

A device with a wildly wrong clock has its edits held back until the clocks agree; the app should
eventually tell the user why. The simulator covers it: clocks within the bound still converge, and
a deferred batch merges once time catches up.
