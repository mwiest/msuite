# 0050: The Android shell creates the SQLite driver

Date: 2026-10-14. Status: accepted. Applies 0037 and 0046 to the database.

## Context

`Replica` takes an `SqlDriver`. On Android the driver needs a `Context`, which common code
doesn't have.

## Decision

`apps/<app>/android` creates `AndroidSqliteDriver(LocalStore.Schema, context, "<app>.db")` and
passes it into the shared app root. New dependency in the shell:
`app.cash.sqldelight:android-driver` (Apache-2, the platform's own SQLite, no `.so`).

## Rejected

- An `expect`/`actual` driver factory in `shared`'s `androidMain`: still needs the `Context`
  passed in, and puts platform code into `shared`.

## Consequences

`shared` stays free of Android APIs. A web target will create its own driver the same way.
