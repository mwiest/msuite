# 0001: Each app keeps its own data store; identity is shared by federation

Date: 2026-07-05. Status: accepted. Formerly OQ-1.

## Context

The suite is several apps (ToDo, Notes, Contacts, Calendar, ...) that should feel like one product
and sync under one identity. The data could live in one shared store, or in one store per app.

## Decision

Each app owns its own op-log and materialized state. Apps link to each other by URI
(`msuite://notes/<id>`), resolved by intent, never through a shared table. Identity and sync
configuration are shared by federation: on first run an app may import them from an installed
sibling, after the user confirms. Pair once per device; siblings inherit.

## Rejected

- One shared store for all apps: bigger models, broader permissions, and the apps could no longer
  be separate F-Droid listings.
- A mandatory hub app holding identity: every app has to work with no siblings installed.

## Consequences

Small per-app models and minimal permissions. Identity is copied into each app's sandbox, so key
rotation must fan out to every app on every device (see 0008), and the root cannot be a
non-exportable KeyStore key (see 0011). A `signature`-level permission could harden the handoff if
F-Droid signing allows one certificate across the apps; the consent dialog is the real gate either
way. Federation ships with app #2; v1 only needs the `IdentityProvider` / `SyncConfig` interface.
