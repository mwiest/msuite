# 0054: Field IDs 1 to 9 are reserved for all apps; record types are Int64 IDs

Date: 2026-10-14. Status: accepted. Details 0035 (record type field) and 0002 (tombstones).

## Context

Every record has a record type (0035) and a `deleted` tombstone (0002), whatever the app. Their
field IDs must not clash with an app's own fields, and the type's value needs an encoding.

## Decision

- Field IDs 1 to 9 are reserved for fields every app shares, defined in `CoreFields`
  (`core/model`): `TYPE = 1`, `DELETED = 2`. App fields start at 10.
- `TYPE` holds a `Value.Int64` record type ID, defined by each app and stable like field IDs
  (0006, 0043). It is written when the record is created.
- `DELETED` holds a `Value.Bool`, `true` once the record is deleted.

## Rejected

- Text type names (`"todo.task"`): self-describing in a dump, but bigger, and names invite renames
  that 0006 wants to cost nothing.

## Consequences

Generic code (snapshots, debugging tools, a future compaction) can find types and tombstones
without knowing the app. Type IDs need a registry per app, kept next to its field IDs.
