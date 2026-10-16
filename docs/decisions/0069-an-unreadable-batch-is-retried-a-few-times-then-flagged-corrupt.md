# 0069: An unreadable batch is retried a bounded number of times, then kept and flagged corrupt

Date: 2026-10-16. Status: accepted. Amends the consequence of 0068.

## Context

0068 skips a fetched batch that fails to decode and leaves it unmarked, so every later sync retries
it. That protects against a torn write that heals (a folder briefly exposing a half-written file),
but a genuinely corrupt object is then re-downloaded and re-decoded on every sync forever, and no
client can repair it: keys are content-addressed and the plug's put is create-only (0003, 0029), so
the correct bytes hit the existing key and are ignored. The cost would be paid forever, invisibly.

## Decision

A failed decode is recorded per plug and key (`unreadable_batch`: attempts plus a corrupt flag).
After `MAX_DECODE_ATTEMPTS` (3) failures, `Replica.unreadable` flags the key corrupt: it is not
fetched again, the bytes stay in the backend untouched, and the flag is kept so a later UI can
surface it. A key that decodes on any earlier attempt simply merges and is marked ingested, as
before. In practice the attempts land on separate syncs, since the retry is a later pull.

## Rejected

- Retry forever (0068 as landed): a permanent tax on every sync for an object that can never heal.
- Flag the key corrupt on the first failure: a torn write that would have healed is then hidden
  forever, which is the case 0068 exists for.
- Delete the object from the backend: the client may not own the store, and the plug contract has
  no delete (0003).

## Consequences

A sync costs a bounded number of retries per broken object, and the local flag names exactly what
is broken. Healing a corrupt object is an operator action (or a future retry affordance on the
flag), not something sync does by itself. The simulator asserts that a corrupt key stops being
fetched, over `MemoryState` and over `Replica` with SQLite, which needs migration 1.
