# 0035: Per-field state is one generic table, shared by every app

Date: 2026-10-11. Status: accepted. Replaces the storage layout of 0006 (typed columns plus a side
table for unknown fields).

## Context

With typed columns (`title`, `title_hlc`, `title_device`, ...), every app needs its own schema and
its own merge code, and unknown fields need a separate side table. `core` is meant to serve four
or more apps with one sync layer.

## Decision

`core/storage` keeps all synced state in one table:

```
field_state(record_id, field_id, value BLOB, hlc, device_id)   primary key (record_id, field_id)
```

- `value` is the field's value encoded with the batch encoder (0016).
- The record type is an ordinary field with a reserved ID, written when the record is created.
- `merge()` in `core/sync` works on `(record_id, field_id)` cells, so it is the same for every app.
- A field ID the app doesn't know is just a row it doesn't read, so unknown fields are retained
  for free (0006) and go into snapshots like any other row.
- Apps read typed models through their own projection code. Indexes, or projection tables
  rebuilt from `field_state`, can be added for queries if needed, with no format change.

## Rejected

- Typed columns per app plus a side table for unknown fields (0006 as written): per-app schemas
  and merge code, and two places to look for a field.

## Consequences

One schema and one merge for all apps; adding a field needs no migration. Queries such as "tasks
of list X ordered by position" are joins over `field_state`, fine at ToDo scale. Type safety moves
from the database into the projection code, which needs its own tests.
