# 0048: An app's shared module also targets jvm, for its tests

Date: 2026-10-14. Status: replaced by 0051.

## Context

The ToDo projection (typed lists and tasks read from `field_state`), its read-time rules and
simulator invariants 14 to 16 need tests. Tests run with kotest on `jvm` (0038), but
`apps/todo/shared` built only for Android.

## Decision

`apps/<app>/shared` targets `android` and `jvm`. Its logic is tested in `jvmTest` with kotest,
like `core/*`. Logic and UI stay in the one module, in separate packages.

## Rejected

- A separate `apps/<app>/domain` module for the logic: keeps Compose out of the tests and the
  rules, but makes three modules per app.
- Android host tests in `shared`: kotest needs extra JUnit 5 setup on AGP, and tests would
  diverge from 0038.

## Consequences

The Compose UI also compiles for desktop JVM, so builds are slower and desktop Compose artifacts
enter the graph (not the APK). Nothing stops UI code from leaking into the logic but review; if
that becomes a problem, split the logic into its own module.
