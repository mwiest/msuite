# 0037: App code lives in KMP modules; the Android app is a thin shell

Date: 2026-10-11. Status: accepted; targets of `shared` narrowed by 0048. Narrows 0020 (navigation artifact).

## Context

`apps/todo` was a plain Android module, so its screens could never be shared, while `core/design`
was Compose Multiplatform "for web later". The catalog also pinned the Android-only
`androidx.navigation:navigation-compose`, against 0012. AGP 9 no longer lets Android modules
apply the Kotlin Multiplatform plugin together with `com.android.application` or
`com.android.library`.

## Decision

- Each app is two modules: `apps/<app>/shared`, a KMP module with Compose Multiplatform holding the
  screens, view logic and app wiring in `commonMain`; and `apps/<app>/android`, an Android
  application holding `MainActivity`, the manifest, resources and Android-only implementations
  (SAF, KeyStore, camera).
- KMP modules use AGP's `com.android.kotlin.multiplatform.library` plugin.
- Navigation is the Compose Multiplatform artifact
  `org.jetbrains.androidx.navigation:navigation-compose`, with type-safe routes.
- The boundary rule covers both: `apps/<a>/*` never depends on `apps/<b>/*`.

## Rejected

- Android-only app modules, with a separate UI for web later: the UI would be written twice, and
  CMP in `core/design` would buy little.
- One KMP module that is also the Android application: not supported by AGP 9.

## Consequences

The web app (`web/README.md`) can reuse the app UI instead of writing its own. Platform features
need an interface in `shared` and an implementation in `android`. The F-Droid build `subdir` for
ToDo is `apps/todo/android`.
