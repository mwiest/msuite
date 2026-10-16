# 0066: Live plugs are put/list/get; the manual plug is a snapshot export/import

Date: 2026-10-16. Status: accepted. Realizes the plug interface of 0003; leaves 0029's key grammar
to step 6.

## Context

v1 step 5 builds two things: the backend-plug contract the synced-folder plug (step 8) and the
relay plug (step 9) will implement, and the manual plug that ships first. The manual plug is a
single file: the user exports the whole state, carries the file over, imports it. It has no list
and no namespace, so the `put`/`list`/`get` shape of 0003 doesn't describe it (the table in
`architecture.md` marks its namespace and `list` as "-").

## Decision

- `BackendPlug` is `put`/`list`/`get` in `core/sync` (decision 0003): append-only, create-only,
  atomic, opaque bytes under caller-chosen keys. The key grammar (decision 0029) is built with it
  at step 6, when `groupId` and the ciphertext hash exist. Contract-tested now with an in-memory
  `MemoryPlug`.
- The manual plug is separate: `Replica.snapshot()` plus a merge path through the same `ingest`
  reducer, exposed as `ManualSync.export()` / `import(bytes)` over `ProtoBatchEncoder`. No key, no
  list; the user is the transport.

## Rejected

- The manual plug implementing `put`/`list`/`get` with a single degenerate key: it needs an object
  name, which needs `groupId` before step 6, and a plug that can't list batches or do list-and-diff
  is not that contract.
- Skipping the interface until step 8: the plan puts it in step 5, and the folder and relay plugs
  would build against an unfrozen contract.

## Consequences

The live plugs have one frozen shape to implement and share a contract test. The manual path is a
second, smaller code path, but it exercises the same merge and is what Layer 2 drives at step 5.
