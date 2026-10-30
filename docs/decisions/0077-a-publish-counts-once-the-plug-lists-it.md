# 0077: A publish counts once the plug lists it

Date: 2026-10-30. Status: accepted.

## Context

A plug's watermark (decision 0036) moved past a delta as soon as `put` returned. On Dropbox
through SAF, `put` can succeed into a place the listing never shows: two devices creating the
group folder at once each got one, Dropbox renamed the loser to `{groupId}_<n>`, and the batch
written there was never listed again. The writer's watermark had moved on, so it never resent
those cells and the peer never got them (step 8b, T3 re-run on 2026-10-30). Any eventually
consistent folder can lose a write the same way.

## Decision

`put` alone no longer settles a publish. The replica records each published key as unconfirmed,
with the watermark from before it, in `unconfirmed_publish`. Every successful listing of the plug
confirms the keys it shows; a key missing from [`MAX_CONFIRM_MISSES`] (3) listings in a row is
given up: the watermark goes back to before it (never forward), and the next publish sends its
cells again under a fresh key. The folder plug separately adopts twin folders, which recovers what
a twin already holds.

## Rejected

- Confirming by reading the key back right after `put`: the writer's own view is what was wrong,
  so it would confirm a write into the twin.
- Keeping the watermark until the key is listed: every debounce in between would send the same
  cells again.
- Time-based expiry: listings are what can show a key, and they only run in the foreground;
  counting them needs no clock.
- Only adopting twin folders: fixes this one cause, not a write lost any other way.

## Consequences

A lost publish is resent about 30 s later (three 10 s polls), costing a duplicate object if the
first one was merely slow to list; ingest is idempotent, so duplicates are harmless. A plug whose
listing silently returns nothing (step 8b, F6) makes every publish look lost and resent, which is
wasteful but not wrong. Revisit if a plug can confirm a put directly (the relay can).
