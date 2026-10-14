# 0047: UI text uses Compose Multiplatform resources

Date: 2026-10-14. Status: accepted. Settles the mechanism left open by 0027.

## Context

The screens live in `commonMain` of `apps/<app>/shared` (0037), which has no Android `Context`.
Every string ships in English and German (0027).

## Decision

Strings live in `apps/<app>/shared/src/commonMain/composeResources/values/strings.xml` and
`values-de/strings.xml`, read with `stringResource(Res.string.x)`. New dependency:
`org.jetbrains.compose.components:components-resources` (Apache-2), versioned with Compose
Multiplatform.

## Rejected

- Android `strings.xml` in the shell: every string needs plumbing into common code, and the web
  target would need its own copy.

## Consequences

Keys are compile-checked accessors, and the same files serve a later web target. Shared
components in `core/design` that need text get their own `composeResources` the same way.
