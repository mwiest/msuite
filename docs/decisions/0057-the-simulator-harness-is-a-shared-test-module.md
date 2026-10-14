# 0057: The simulator harness is a shared test-support module

Date: 2026-10-15. Status: accepted. Extends 0007 (the simulator) to apps.

## Context

The simulator's harness (devices with skewed clocks, failing pushes, partial and reordered pulls,
quiescence) lived in `core/sync`'s test sources, which other modules can't use. The ToDo rules
(invariants 14 to 16) need the same conditions, as will later apps.

## Decision

`core/sync-testing` is a Kotlin/JVM library (`msuite.jvm-library`) holding the harness: `Cluster`,
`SimDevice`, the shared `NetStep`s and their generator, `SimState` with the in-memory and
`Replica` backends, and `TestClock`. Only test source sets depend on it. Each app extends `Cluster`
with its own write steps and invariants in its own tests.

## Rejected

- A separate, lighter harness in each app's tests: duplicated code, and the app rules tested under
  milder conditions than the merge core.
- Gradle test fixtures: not supported for KMP modules' `jvmTest`.

## Consequences

One harness for all layers and apps. A change to it reruns every simulator. The module carries
kotest and sqlite-jdbc as `api` dependencies, which is fine since nothing that ships depends on it.
