# 0044: Sort keys are base62 fractional indexes with a 2-character random suffix

Date: 2026-10-13. Status: accepted.

## Context

0004 makes manual order an LWW `position` field holding a fractional-index key with a short
random suffix, but leaves the key format open. The format ends up in stored data and batches.

## Decision

- Keys follow rocicorp/fractional-indexing: base62 digits (`0-9A-Za-z`), a length-prefixed
  integer part, then a fraction without trailing zeros. They compare by plain character order.
- Every generated key gets a 2-character base62 suffix from an injected `Random`; the last
  character is never `0`. Two devices inserting into the same gap get the same key only if their
  suffixes match (1 in 3782); then the item ID breaks the tie (0004).
- If the plain key is a prefix of the upper bound, adding the suffix could overshoot it, so
  generation moves closer to the upper bound until the suffixed key fits.

## Rejected

- Base36 lowercase: easier to read, but longer keys.
- No suffix: concurrent inserts into one gap would always get equal keys, leaving their order to
  the item IDs.
- A longer suffix: fewer equal keys, but every key grows by it.

## Consequences

A widely used, tested scheme, checked against its reference outputs. Each insert adds 2
characters, and repeated inserts into one gap grow keys further; rebalancing stays deferred
(0004). Storage must compare keys bytewise (binary collation), not locale-aware.
