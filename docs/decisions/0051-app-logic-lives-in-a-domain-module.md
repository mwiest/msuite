# 0051: An app's logic lives in its own domain module

Date: 2026-10-14. Status: accepted. Replaces 0048; narrows 0037 (modules per app).

## Context

The ToDo projection (typed lists and tasks read from `field_state`), its read-time rules and
simulator invariants 14 to 16 need tests, which run with kotest on `jvm` (0038).
`apps/<app>/shared` is a Compose module built only for Android. 0048 added a `jvm` target to it,
which compiles the whole UI for desktop JVM just to run the logic tests, and leaves the line
between logic and UI to review.

## Decision

Each app has three modules:

- `apps/<app>/domain`: a plain KMP library (android + jvm, `msuite.kmp-library`), package
  `com.msuite.<app>.domain`. Field IDs, the projection, read-time rules, commands, and any screen
  state worth testing, without Compose types. Tested in `jvmTest` like `core/*`.
- `apps/<app>/shared`: Compose UI and the app root, android only; depends on `domain`.
- `apps/<app>/android`: the thin shell (0037).

## Rejected

- A `jvm` target on `shared` (0048): the UI compiles twice, desktop Compose artifacts enter the
  build, and nothing but review keeps Compose out of the logic.
- Android host tests in `shared`: kotest needs extra JUnit 5 setup on AGP, and tests would
  diverge from 0038.

## Consequences

The compiler keeps Compose out of the logic, and logic tests stay light. Each app has one more
module, and logic a screen needs tested must live in `domain`, so screens stay thin.
