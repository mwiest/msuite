# 0064: Settings are a navigation route; the theme is a local setting

Date: 2026-10-15. Status: accepted. Applies the stack's multiplatform-settings and Navigation
Compose choices.

## Context

v1 has a settings screen: the theme now, the backend plug in step 5. The theme is a device-local,
non-secret preference, never synced.

## Decision

- Settings is a full-screen route; the app's first navigation (type-safe routes, Navigation
  Compose Multiplatform). A gear in the tasks screen's top bar opens it.
- The theme is System (default), Light or Dark. `TodoSettings` in `apps/todo/domain` keeps it in
  multiplatform-settings; the Android shell creates the `Settings` on `SharedPreferences` named
  `todo`. `MsuiteTheme` takes a `dark` flag, so `core/design` knows nothing of settings.
- Tests use `MapSettings` from `multiplatform-settings-test` (same library, test only).

## Rejected

- A theme dialog from the menu: no navigation yet, but step 5's plug settings need a screen.
- Dynamic color (Material You): native feel on Android 12+, but an Android-only color scheme
  passed from the shell into `core/design`.

## Consequences

Navigation is in place for step 5. `ThemeMode` lives in the ToDo domain; it moves to a core
module when a second app needs it.
