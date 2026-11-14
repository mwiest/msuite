# 0088: Done tasks sort by completion time, stored as DONE_AT

Date: 2026-11-14. Status: accepted. Replaces the done-task order of 0065.

## Context

Done tasks sorted by position (0065), so a task done just now could land anywhere in a long done
group. 0065 rejected ordering by completion time because no field held that time.

## Decision

- A task's done state is `DONE_AT` (field 17, `Int64` epoch milliseconds from the device's wall
  clock); absent means open. Reopening clears it. The `DONE` flag (field 16) is no longer written
  and stays reserved.
- Done tasks show after open ones, the latest done first, ties by ID. Open tasks keep
  `(position, id)`.
- Migration, at every app start: each task with `DONE = true` gets `DONE_AT` = the `pt` of that
  flag cell's HLC (when it was set), and the flag is cleared in the same write, so it never runs
  twice for a task. A task already holding `DONE_AT` only has its flag cleared. All devices derive
  the same value from the same synced cell, so concurrent migrations agree.

## Rejected

- Keep `DONE` and add `DONE_AT` beside it: two fields to keep consistent, and a concurrent write
  could leave one set and the other not.
- Sort by the `DONE` cell's HLC at read time, no new field: the order would depend on sync
  metadata, and any rewrite of the flag would move the task. Used once for the migration only.
- A new `Instant` value type in `core/model`: a codec change for one field; `Int64` is enough.

## Consequences

A device on an older build reads every task as open and its done toggles write the legacy flag,
which the next start of a newer build migrates; all devices should update. An HLC can run ahead of
the wall clock (bounded by `HlcClock.MAX_AHEAD`), so a migrated time can be slightly late. A
"Completed" section or showing the completion time can now read `DONE_AT`.
