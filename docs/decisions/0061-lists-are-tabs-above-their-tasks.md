# 0061: Lists are tabs above their tasks

Date: 2026-10-15. Status: accepted.

## Context

The ToDo app has several lists; the user needs to switch between them and manage them.

## Decision

The main screen shows the lists as scrollable tabs at the top, like Google Tasks, with the
selected list's tasks below. List actions (rename, delete, reorder) and creating a list sit in
the top bar.

## Rejected

- A navigation drawer: scales to many lists and hosts settings, but switching takes two taps and
  the lists are out of sight.
- A home screen of lists: the simplest navigation, but an extra step to every list's tasks.

## Consequences

Switching is one tap. Many lists crowd the tabs; revisit if users keep dozens.
