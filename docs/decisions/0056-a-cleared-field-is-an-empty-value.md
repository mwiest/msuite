# 0056: A cleared field is a cell with an empty value

Date: 2026-10-15. Status: accepted. Details 0045.

## Context

A field can be cleared, e.g. a task's due date removed. A cell can't simply be deleted: merge needs
its stamp, or an older value from another device would come back.

## Decision

Clearing writes the cell with an empty value (`ValueCodec.encode(null)`), stamped like any edit.
Empty bytes decode as absent, so readers see the field as unset.

## Rejected

- A `Value.None` type with its own field number: explicit in a dump, but a format change, and old
  readers would see an unknown type, which is absent anyway.

## Consequences

No format change, and clearing races by LWW like any edit. A cleared cell stays in storage and in
snapshots, like a tombstone.
