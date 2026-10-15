# 0065: Done tasks sort last; the last open list is remembered

Date: 2026-10-15. Status: accepted. Refines 0004 and 0063.

## Context

Done tasks mixed with open ones by position, and the app reopened on the first list.

## Decision

- A list's tasks show open ones first, then done ones, each by `(position, id)`. Done tasks are
  grayed out and can't be dragged.
- A move places a task among its list's tasks with the same done state, so its neighbours are in
  position order. Completing or reopening a task keeps its position; it reappears where it was
  in its group.
- The open list's ID is a device-local setting (`TodoSettings.lastList`, 0064), not synced; a
  deleted one falls back to the first list.

## Rejected

- A collapsible "Completed" section: tidier for long lists, but more UI for a proof of concept.
- Sorting done tasks by completion time: needs a timestamp field; the done cell's HLC isn't
  meant for display.
- Remembering the list only across activity recreation (`rememberSaveable`): lost when the
  process dies, which is most app restarts.

## Consequences

Each device opens on its own last list. Revisit a "Completed" section if done tasks pile up.
