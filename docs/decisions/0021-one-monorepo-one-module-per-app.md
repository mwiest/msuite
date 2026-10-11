# 0021: One monorepo; one module, applicationId and tag prefix per app

Date: 2026-07-05. Status: accepted.

## Context

Several apps share `core`, design and build configuration but are released and listed on F-Droid
separately. Separate repos drift; one repo can rot into a tangle.

## Decision

One git repo, one Gradle multi-module build:

- One version catalog (`gradle/libs.versions.toml`) and convention plugins in `build-logic/` keep
  versions and build configuration in sync.
- `core/*` is split into small modules by boundary; apps depend only on what they need.
- Apps depend on `core/*`, never on each other.
- Each app is a module with its own `applicationId` (`com.msuite.<app>`), built by F-Droid through
  the recipe's `subdir`, released under its own tag prefix (`todo-v*`), with a `versionCode` that
  only grows per `applicationId`.
- `server/` lives in the repo so the client and relay wire format never drift.

## Rejected

- One repo per app: shared code and versions drift apart.

## Consequences

Releases and tags stay per app even though code is shared.
