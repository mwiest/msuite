# 0038: Logic lives in commonMain, its tests in jvmTest

Date: 2026-10-11. Status: accepted. Replaces "kotest-property in `commonTest`" in 0007 and 0020.

## Context

kotest's JUnit 5 runner is JVM-only. Running kotest from `commonTest` needs kotest's
multiplatform setup, which only pays off if the tests also run on other targets. The JVM is the
target the tests need.

## Decision

Logic stays in `commonMain`; its tests, including the simulator (0007), live in `jvmTest` and run
with kotest on JUnit 5.

## Rejected

- kotest in `commonTest`: more setup, and no second target to run on in v1.

## Consequences

Tests can use JVM-only helpers. Platform differences in `commonMain` code (e.g. on a future web
target) wouldn't be caught by these tests; revisit when the web target is built.
