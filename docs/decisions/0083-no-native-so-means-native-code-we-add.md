# 0083: "No native `.so`" means native code we add

Date: 2026-11-07. Status: accepted. Clarifies 0010, 0011 and 0074.

## Context

The rule keeps native code out of v1 so the build stays Kotlin-only and F-Droid-clean, and so
SQLCipher, libsodium and zxing-cpp stay out until a decision says otherwise. The APK still holds
`libandroidx.graphics.path.so` (four ABIs, 7 to 11 KB each), from
`androidx.graphics:graphics-path:1.0.1`, which Compose UI's `ui-graphics` pulls in. It iterates an
Android `Path`'s segments: on API 34 and later through the platform's `Path.getPathIterator()`, on
API 26 to 33 natively, since no public API exists there. With `minSdk` 26 it is in use.

## Decision

The rule means native code we add or build: our own, or a library we choose for its native part
(SQLCipher, libsodium, zxing-cpp). A prebuilt Apache-2 AndroidX artifact that a dependency we
already use pulls in is allowed, like any other prebuilt AAR from Google Maven. We ship it as is.

## Rejected

- Excluding it from packaging: Compose code that walks a path (outline and shape drawing, Material 3
  shape morphing) would throw `UnsatisfiedLinkError` on Android 8 to 13, at call sites nobody can
  list in full.
- `minSdk` 34 plus the exclusion: drops every device on Android 13 or older for a 10 KB library.
- Building it ourselves from AOSP: brings the NDK into the build, against the rule's spirit, and
  swaps one trusted build for ours.

## Consequences

The rule stays a guard against choosing native libraries, not against AndroidX internals. F-Droid
accepts AndroidX from Google Maven, and Compose apps there ship the same file. A new transitive
`.so` from AndroidX needs no record; one from any other source does.
