# 0089: A merge rewinds the other plugs' watermarks

Date: 2026-11-15. Status: accepted. Builds on 0036, 0077.

## Context

A delta holds the cells above the plug's watermark (0036). A merged cell keeps its original HLC,
so a cell that arrives from elsewhere with an HLC below a plug's watermark never reaches that plug.
This now happens with one plug at a time: a device that pushed to S3, then synced through Dropbox
for a while and switched back, never sends S3 the older cells Dropbox brought in. The settings
keep each backend type's destination so switching back is easy, which makes this more likely. A
manual import while a plug is active has the same gap.

## Decision

When a merge changes cells, every plug with a watermark, except the plug the batch came from, has
its watermark moved to just below the oldest changed cell, never forward. A manual import
(`mergeRemote`) has no source plug, so it moves every plug's.

## Rejected

- Per-plug change tracking (a row per cell and plug): exact, but a new table and a migration for
  what the rewind gets with one query.
- Resetting a plug's watermark when it becomes active after another one: costs a full upload on
  every switch back, and misses the manual import.

## Consequences

The next delta to such a plug also resends this device's cells above the rewound watermark: more
bytes once, never wrong, since merge is idempotent. A cell from a peer with a clock far behind
rewinds far. Sync through two plugs at once still needs more than this (the echo, 0078).
