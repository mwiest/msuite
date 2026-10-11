# 0033: A device joining by pairing merges its data into the group

Date: 2026-10-11. Status: accepted.

## Context

Every device generates its own root on first run and can be used alone. When it later scans
another device's pairing QR code, it may already hold lists and tasks.

## Decision

The joining device keeps its local data. It replaces its root with the group's root, keeps its
`deviceId`, and publishes its full state under the group's namespace; the other devices merge it
like any batch. Anything it had stored under its old root's namespace is abandoned.

## Rejected

- Discarding the joining device's data: silent loss of the user's work.
- Asking the user each time: a choice most users can't judge. Wiping the app first remains possible
  for those who want a clean join.

## Consequences

With 0031, both devices' "My Tasks" become one list. Lists the user created on both devices under
the same name stay two lists. The pairing flow must say that the device's data will be shared with
the group.
