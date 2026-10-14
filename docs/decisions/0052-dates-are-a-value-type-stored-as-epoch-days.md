# 0052: Dates are a value type, stored as epoch days

Date: 2026-10-14. Status: accepted. Adds a value type to 0045.

## Context

A task's due date (v1) is a calendar day. Reminders and calendar events will later need dates
with times. 0045 has only `text`, `bool` and `int64`.

## Decision

`Value.Date` holds a kotlinx-datetime `LocalDate`: a day without time or zone. It is value field
`epochDays = 4`, the days since 1970-01-01 as a zigzag varint. A day count outside what `LocalDate`
holds decodes as absent, like an unknown type. Points in time (reminders) get their own type when
they are built; this decides nothing about them.

## Rejected

- An ISO-8601 string in `text`: readable and sorts right, but the bytes don't say it's a date, and
  every reader parses and validates it.
- Epoch days in `int64`: compact, but indistinguishable from a number.
- A date-time type now: no feature needs one yet (0014, 0034), and its time-zone rules belong with
  reminders.

## Consequences

A due date is two to four bytes and typed. Old readers see a date as an unknown type and treat it
as absent, while merge keeps its bytes. A future date-time type is another field number.
