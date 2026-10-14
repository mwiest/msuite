# 0053: Record references are a value type

Date: 2026-10-14. Status: accepted. Adds a value type to 0045.

## Context

A task points to its list by record ID (a UUID), and later apps will have more such references.
0045 has no type for it.

## Decision

`Value.Ref` holds a `Uuid`: value field `ref = 5`, the 16 raw bytes. Any other length decodes as
absent, like an unknown type.

## Rejected

- The UUID as a string in `text`: 36 bytes or more, untyped, and parsed by every reader.

## Consequences

A reference costs 18 bytes and says what it is. Nothing checks that the target exists: a reference
to a missing or deleted record is the app's read-time rule to handle (0032).
