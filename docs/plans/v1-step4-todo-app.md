# Plan: v1 step 4, the single-device ToDo app

Status: **slice 1 done; slice 2 (domain module and projection) next.** Started 2026-10-14. Part of [`v1-todo.md`](v1-todo.md) step 4.

## Goal

A usable offline ToDo app on one device: the feature set of `v1-todo.md`, stored through
`Replica`, so step 5 only has to add a plug to sync it. The ToDo rules (hidden tasks of a deleted
list, the fixed "My Tasks") are proven by simulator invariants 14 to 16.

## Decisions

- UI text in Compose Multiplatform resources, English and German (decisions 0027, 0047).
- Logic in `apps/todo/domain` (android + jvm, no Compose), UI in `apps/todo/shared` (decision 0051).
- The UI re-reads on change ticks from `Replica` (decision 0049).
- The Android shell creates the SQLite driver (decision 0050).

## Slices

1. [x] **Change ticks and UI text.** `Replica.changes` ticks after each committed write or ingest
   that changed state (tested). Compose Multiplatform resources wired into `shared`; the
   placeholder text moves into `strings.xml`, English and German. No test for the text: `shared`
   has no test target (decision 0051); the APK was checked to carry both string files.
2. [ ] **Domain module and projection.** `apps/todo/domain` with ToDo field IDs, the fixed
   "My Tasks" ID (decision 0031), and the projection from cells to typed lists and tasks, sorted
   by `(position, id)`, hiding tasks of deleted lists (decision 0032). Tested in `jvmTest`.
3. [ ] **Commands.** Create, rename, delete and reorder lists; create, edit, complete, star, delete
   and reorder tasks; each a `Replica.write`. Tested against the projection.
4. [ ] **Simulator invariants 14 to 16.** The ToDo rules under random schedules (`testing.md`).
5. [ ] **App wiring.** The shell creates the driver and the `Replica` with a stable device ID and
   passes it into the app root; the UI observes the projection. See Open questions.
6. [ ] **Screens.** Tasks of a list (checkbox, star, FAB), the task editor, list management,
   settings (theme), with navigation. One slice per screen if they grow.

## Open questions

- Where the device ID lives: a row in the database (survives with the data it stamped), or
  multiplatform-settings. Decide before slice 5.
- Due dates: kotlinx-datetime `LocalDate` encoded as what `Value` type. Decide in slice 2.

## How to verify

`./gradlew allTests` covers the projection, commands and invariants 14 to 16. On a device: create
lists and tasks, reorder, complete, delete a list, kill the app, and everything is still there.
