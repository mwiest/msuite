# 0049: The replica signals changes; the UI re-reads

Date: 2026-10-14. Status: accepted.

## Context

Lists must refresh after a local edit and, later, after a sync ingest. All writes go through the
one `Replica` per database.

## Decision

`Replica` exposes a `StateFlow` that ticks after each committed write or ingest that changed
state. The app's projection re-reads its state on each tick.

## Rejected

- SQLDelight's coroutines extensions (Flow queries): another dependency, and its queries would
  have to be exposed through `Replica` and `LocalStore` to the app.

## Consequences

No new dependency. Every change re-reads the whole state, fine at ToDo scale; Notes or larger
data may want finer-grained queries, which is when to revisit.
