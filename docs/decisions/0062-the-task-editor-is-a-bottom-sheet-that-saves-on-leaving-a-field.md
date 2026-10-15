# 0062: The task editor is a bottom sheet that saves on leaving a field

Date: 2026-10-15. Status: accepted.

## Context

Tapping a task opens its editor: title, notes, due date, star, done, delete. Every write is a set
of HLC-stamped cells that later travel as sync batches, and re-reads the whole state (0049).

## Decision

- The editor is a modal bottom sheet over the tasks screen, without navigation.
- Title and notes are written when their field loses focus or the sheet closes, and only if they
  changed. Star, done and the due date are written at once.
- The due date is picked with Material 3's `DatePickerDialog`, and can be cleared (0056). It is
  shown with localised month names from the resources.

## Rejected

- A full-screen route: more room for notes and proper back handling, but wires navigation now.
- Writing on every keystroke: never loses text, but one write and one full re-read per character,
  and many cells to sync.
- An explicit Save button: cancellable, but edits get lost on back.
- Typing the date: trivial, but locale formats and parsing.

## Consequences

No navigation until a screen needs it (settings at the latest). A crash while typing loses that
field's edit. Long notes have less room than on a full screen.
