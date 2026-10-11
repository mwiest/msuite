# 0032: Tasks whose list is deleted are hidden everywhere

Date: 2026-10-11. Status: accepted.

## Context

Device A deletes a list while device B, offline, adds a task to it or edits one of its tasks.
Per-field LWW merges both without conflict, leaving tasks that point to a deleted list.

## Decision

A read-time rule: a task whose current `listId` refers to a deleted list is not shown anywhere. The
task keeps its state and is synced like any other record. Deleting a list writes only the list's
`deleted` field; it doesn't touch its tasks.

## Rejected

- Moving orphaned tasks to "My Tasks": a write that two devices could make differently, and it
  revives tasks the user meant to delete with the list.
- Tombstoning every task when deleting its list: misses tasks added concurrently on another device.

## Consequences

One write per list deletion, and the rule is deterministic, so every device hides the same tasks.
Hidden tasks stay in storage until tombstone removal exists (0002). A task moved to another list
with a higher HLC than the deletion stays visible. There is no undelete for lists in v1; if one is
added, its tasks reappear.
