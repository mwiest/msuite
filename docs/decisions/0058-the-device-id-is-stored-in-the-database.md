# 0058: The device ID is stored in the database and created by Replica.open

Date: 2026-10-15. Status: accepted. Builds on 0043.

## Context

A device stamps every cell it writes with its `deviceId` (0043), and its batches are keyed by it
as their single writer (0003, 0029). The app needs the same ID on every start, created once.

## Decision

A one-row table `device` in `core/storage` holds the ID. `Replica.open(driver, clock)` reads it,
or stores a random one on first open, in one transaction. The constructor taking an explicit ID
stays for tests and the simulator.

## Rejected

- multiplatform-settings: no schema change, but a lifecycle apart from the data. Losing the
  database while the setting survives (or restoring one without the other) reuses an ID with a
  restarted HLC, so two batches of one "single writer" could overlap. Also a dependency to wire
  now, with a `Context` in the shell.
- The app root reads the ID through `LocalStore` and passes it to the constructor: the ID and the
  replica could come from different databases.

## Consequences

The ID lives and dies with the cells it stamped: a wiped database gets a fresh ID. A database
copied to a second device (a restored backup) clones the ID; pairing (step 7) must not rely on
backups for that reason. Tests can still pin the ID.
