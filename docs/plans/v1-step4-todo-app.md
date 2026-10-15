# Plan: v1 step 4, the single-device ToDo app

Status: **slices 1 to 5 done; slice 6 (screens) next.** Started 2026-10-14.
Part of [`v1-todo.md`](v1-todo.md) step 4.

## Goal

A usable offline ToDo app on one device: the feature set of `v1-todo.md`, stored through
`Replica`, so step 5 only has to add a plug to sync it. The ToDo rules (hidden tasks of a deleted
list, the fixed "My Tasks") are proven by simulator invariants 14 to 16.

## Decisions

- UI text in Compose Multiplatform resources, English and German (decisions 0027, 0047).
- Logic in `apps/todo/domain` (android + jvm, no Compose), UI in `apps/todo/shared` (decision 0051).
- The UI re-reads on change ticks from `Replica` (decision 0049).
- The Android shell creates the SQLite driver (decision 0050).
- Due dates are `Value.Date`, a `LocalDate` stored as epoch days (decision 0052).
- A task's list is a `Value.Ref` (decision 0053). Record types are `Int64` IDs in the reserved
  `CoreFields.TYPE`, next to `DELETED` (decision 0054).
- The device ID is a row in the database, created by `Replica.open` (decision 0058).
- Lists are tabs above their tasks (decision 0061). Non-secret settings use multiplatform-settings
  (`AGENTS.md` stack).

## Slices

1. [x] **Change ticks and UI text.** `Replica.changes` ticks after each committed write or ingest
   that changed state (tested). Compose Multiplatform resources wired into `shared`; the
   placeholder text moves into `strings.xml`, English and German. No test for the text: `shared`
   has no test target (decision 0051); the APK was checked to carry both string files. Landed:
   `674ab59`.
2. [x] **Domain module and projection.** `apps/todo/domain` with ToDo field IDs, the fixed
   "My Tasks" ID (decision 0031), and the projection from cells to typed lists and tasks, sorted
   by `(position, id)`, hiding tasks of deleted lists (decision 0032). Tested in `jvmTest`. First
   run writes "My Tasks" with its title in the device language; deleting it is honoured like any
   list (decision 0055). Value types `Date`, `Ref` and the reserved core fields came first:
   `94d07a8`, `93f239a`; landed: `8b69bc1`.
3. [x] **Commands.** Create, rename, delete and reorder lists; create, edit, complete, star, delete
   and reorder tasks; each a `Replica.write`. Tested against the projection. New tasks go to the
   top; clearing a field writes an empty value (decision 0056); malformed values decode as absent
   (`74de47a`). Landed: `33e017c`.
4. [x] **Simulator invariants 14 to 16.** The ToDo rules under random schedules (`testing.md`).
   The harness moved into `core/sync-testing` first (decision 0057, `f1317df`); the commands take
   an ID source (`d2a97ad`); edits are guarded against wrong IDs (`0e45c4d`). Landed: `8ece7f2`.
5. [x] **App wiring.** The shell's `TodoApplication` opens one `Replica` per process; the device ID
   is a row in the database (decision 0058, `8f8b2c7`); the replica runs off the UI thread
   (decision 0059, `1c3602d`); `TodoApp(replica)` creates "My Tasks" and shows `todoStates()`.
   Backup is off (decision 0060). Checked on an emulator: "My Tasks" appears once, also after a
   restart. Landed: `7e47dfa`.
6. [ ] **Screens**, one slice each, each checked on an emulator:
   1. [x] **Tasks of a list.** Lists as tabs (decision 0061), the selected list's tasks with
      checkbox and star, a FAB that adds a task by title in a bottom sheet. A deleted selected
      list falls back to the first (`TodoState.selected`, tested). Icons are vector drawables in
      the resources, no icon library. Checked on an emulator, also after a restart. Landed:
      `7dd7fbb`.
   2. [ ] **Task editor**, a bottom sheet (decision 0062): title, notes, due date, star, done,
      delete. Navigation comes with the first screen that needs it.
   3. [ ] **List management**: create, rename, delete, reorder lists; reorder tasks.
   4. [ ] **Settings**: the theme (system, light, dark) in multiplatform-settings, created by
      the shell (it needs a `Context`).

## Open questions

None.

## How to verify

`./gradlew allTests` covers the projection, commands and invariants 14 to 16. On a device: create
lists and tasks, reorder, complete, delete a list, kill the app, and everything is still there.
