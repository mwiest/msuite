# 0003: Backend plugs are append-only put/list/get with content-addressed names

Date: 2026-07-05. Status: accepted; name grammar replaced by 0029. Formerly OQ-3.

## Context

A dumb store with more than one writer quietly adds a second merge layer below the app's:
Syncthing writes `.sync-conflict` files, S3 and WebDAV overwrite last-write-wins. All merging must
stay in the client.

## Decision

Every plug implements one narrow, append-only interface. v1 builds `put` / `list` / `get`;
capability-gated `delete` and a capability descriptor come later, with snapshots and the relay.

- `put(name, bytes)` is create-only and atomic: a listed name always points to a complete object.
- `list(prefix)` returns names only. `get(name)` returns bytes, re-verifiable against the hash.
- Names are `v{fmt}_{type}_{deviceId}_{hlcLo}_{hlcHi}_{hash}` in the charset `[A-Z0-9_]`. `hash`
  is SHA-256 over the ciphertext; `hash` and `deviceId` are unpadded uppercase base32. One writer
  per batch. Uppercase-only avoids clashes on case-insensitive stores.
- A batch's ciphertext is computed once (salt fixed at creation), so a retried `put` sends
  identical bytes to the same name.
- "Changes since X" is list-and-diff: `list(prefix)` minus the names already ingested. No server
  cursor.

The per-plug realization is in `docs/architecture.md`.

## Rejected

- One overwritten object per device or per record: clobbers on every store that isn't create-only.
- A server-side cursor: only the relay could offer one. It may be added there later without
  changing the names.

## Consequences

Concurrent writers always produce distinct objects, so create-only and overwrite backends behave
the same and the app's LWW merge is the only merge layer. Enumeration is O(objects) until
snapshots prune. Names leak some metadata (0009).
