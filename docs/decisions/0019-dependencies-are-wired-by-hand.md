# 0019: Dependencies are wired by hand, one composition root per app

Date: 2026-07-05. Status: accepted.

## Context

The apps and the relay need dependency wiring. The common Android choices are Google libraries,
Android-only, or reflection-based.

## Decision

Manual constructor injection, with one composition root per app. If wiring grows, Koin may be added
in the app layer only. The relay never uses reflection-based DI (0017).

## Rejected

- Hilt / Dagger: Android-only and from Google.
- Any reflection-based DI in the relay: blocks the cheap GraalVM path.

## Consequences

No annotation processing and no framework to learn. More boilerplate in each composition root as
an app grows; that is the signal to revisit (Koin, app layer only).
