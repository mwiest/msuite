# 0059: The replica runs its work in its own coroutine context

Date: 2026-10-15. Status: accepted.

## Context

SQLDelight's drivers block. `Replica`'s suspend functions ran them on the caller's thread, so a
command launched from the UI would hit SQLite on the main thread.

## Decision

`Replica` takes a `CoroutineContext` (default `Dispatchers.Default`, available on every target)
and runs every operation in it, inside its `Mutex` and transaction.

## Rejected

- Switching in the UI (`withContext`, `flowOn`): `core/sync` stays unchanged, but every caller,
  including the sync plugs and background work to come, must remember it.

## Consequences

Any caller may call the replica from the main thread. Tests can pin the context.
