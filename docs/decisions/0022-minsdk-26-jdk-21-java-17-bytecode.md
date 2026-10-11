# 0022: minSdk 26; build on JDK 21; emit Java 17 bytecode

Date: 2026-07-05. Status: accepted.

## Context

The apps rely on Android KeyStore and file-based encryption at rest (0011). The build needs a
current JDK; the output must run on Android and on cheap relay hosts.

## Decision

- `minSdk` 26 (Android 8.0): a clean KeyStore and FBE baseline with few legacy shims.
- `compileSdk` and `targetSdk` follow the latest stable Android release.
- The build runs on JDK 21 (LTS); Kotlin `jvmTarget` and Java compatibility are 17.

## Rejected

No alternatives were recorded.

## Consequences

Devices older than Android 8.0 are not supported. A newer JDK on the machine (e.g. a JDK 25 bundled
with Android Studio) can't run the current Gradle; the build must be pointed at JDK 21.
