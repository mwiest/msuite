# 0068: A batch that fails to decode is skipped and retried, not ingested

Date: 2026-10-16. Status: accepted. Fills the open question in the v1 plan.

## Context

`ProtoBatchEncoder.decode` throws `MalformedBatch` on bytes that aren't a batch. Real plugs hand
the client whatever is at a key: a synced folder can briefly expose a half-written file, and any
store can hold a corrupted object. The client must choose between treating it as done or retrying.

## Decision

When a fetched batch fails to decode, the client skips it: it is *not* marked ingested, so a later
sync retries it, and it is logged (once the plug layer has logging). `decodeOrNull` in `core/sync`
turns the throw into `null` for that path. It does not merge anything and does not touch the clock.

## Rejected

- Mark it ingested and log: a torn or corrupted write would be hidden forever, and the data behind
  the key would never arrive.
- Fail the whole sync: one bad object would block every other key.

## Consequences

A permanently corrupt object is retried on every sync until an operator removes it; snapshot
pruning (0002) and the folder plug's write-temp-then-rename (step 8) keep this rare. The same rule
covers a value blob, which already decodes as absent per 0056.
