# 0026: Spotless (ktlint) formats, detekt checks

Date: 2026-07-05. Status: accepted.

## Context

One developer plus agents write the code; style and common mistakes should be caught by tools, not
reviews.

## Decision

Spotless with ktlint formats Kotlin and Gradle Kotlin scripts; detekt runs static analysis. Both
are applied from the `build-logic` convention plugins and gate CI (0025).

## Rejected

No alternatives were recorded.

## Consequences

`spotlessApply` fixes formatting locally; detekt findings must be fixed or explicitly suppressed.
