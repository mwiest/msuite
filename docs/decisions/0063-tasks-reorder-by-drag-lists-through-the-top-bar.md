# 0063: Tasks reorder by long-press drag (Reorderable); lists are managed from the top bar

Date: 2026-10-15. Status: accepted. Builds on 0061.

## Context

Lists and tasks need create, rename, delete and reorder (manual order, 0004). Compose
Multiplatform has no built-in drag-to-reorder for lazy lists. Deleting a list hides its tasks
(0032) and has no undo.

## Decision

- Tasks reorder by long-press drag, with `sh.calvin.reorderable:reorderable` (Calvin-LL,
  Apache-2.0, KMP including js and wasm, depends only on Compose foundation, animation and
  runtime). The new position is written once, when the drag ends.
- A trailing "New list" tab creates a list. The top bar's overflow menu acts on the selected list:
  rename, move left or right, delete. "My Tasks" offers no delete (0031).
- Deleting a list asks for confirmation, naming how many tasks go with it.

## Rejected

- Hand-rolled drag on Compose pointer input: no dependency, but the fiddliest UI code in v1
  (autoscroll, keys) with no test target in `shared` (0051).
- Move up/down actions for tasks: trivial, but clunky for long lists.
- A "Manage lists" sheet: everything in one place, but one more surface, less direct.
- Delete with an undo snackbar: faster, but undo is a second write that syncs, and more logic.

## Consequences

One UI dependency in `apps/todo/shared`. Dragging tabs is not offered; lists move one step at a
time from the menu.
