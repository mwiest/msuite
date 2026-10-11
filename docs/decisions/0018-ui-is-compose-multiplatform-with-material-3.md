# 0018: UI is Compose Multiplatform with Material 3

Date: 2026-07-05. Status: accepted.

## Context

All apps share one design language, kept in `core/design`. 0012 requires that shared code can
reach web later.

## Decision

Compose Multiplatform with Material 3. `core/design` keeps its theme and shared components in
`commonMain`. For now it builds only the android target.

## Rejected

- AndroidX Compose: Android-only, so `core/design` couldn't live in `commonMain`.

## Consequences

The shared design can move to web (wasmJs) or desktop later. Compose Multiplatform releases trail
AndroidX Compose slightly, and the Kotlin and Compose compiler versions have to move together.
