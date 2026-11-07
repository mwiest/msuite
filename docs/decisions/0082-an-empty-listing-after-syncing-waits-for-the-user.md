# 0082: An empty listing after syncing waits for the user

Date: 2026-11-07. Status: accepted. Refines 0077 and the recovery rule.

## Context

SAF reports a provider it can't see, or a grant that is gone, as an empty folder, not an error
(seen with Dropbox on Android 16). A pull then pruned every `ingested_batch` row, so the next good
listing re-read the whole folder, and Settings said nothing until a write failed. A relay whose
storage was reset looks the same. The plug can't tell either case from a folder the user really
emptied, which the recovery rule exists to refill.

## Decision

- A pull whose listing is empty, on a device that has synced through that plug before, stops
  there: no ingest, no pruning, no recovery snapshot, no confirm bookkeeping. "Synced before"
  means a batch was ingested from the plug, or every publish to it was confirmed by a listing; an
  unconfirmed publish doesn't count, so the resend of 0077 still runs.
- `LiveSync.emptyHeld` reports the hold; Settings says the folder or relay looks empty, with a
  button to confirm that it is. `acceptEmpty` lets the following pulls prune and recover as
  before, until a listing shows something again.
- It applies to every plug, in `core/sync`.

## Rejected

- Only show the folder's identity (root name, child count) and keep pruning: still a silent full
  re-read and a surprise republish.
- Believe an empty listing after N pulls in a row: a lost grant still ends in pruning, later, and
  the user is not told.

## Consequences

A lost grant no longer costs bookkeeping or republishes anything, and the user learns about it.
Emptying a folder on purpose takes one tap per device that synced through it. The hold lives in
memory: after a restart a held device asks again, which is harmless.
