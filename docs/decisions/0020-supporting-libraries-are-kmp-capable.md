# 0020: Supporting libraries are KMP-capable

Date: 2026-07-05. Status: accepted; navigation artifact narrowed by 0037, test location by 0038.

## Context

Besides the big choices (storage, crypto, encoding, UI), the apps need navigation, time, IDs,
logging, settings, HTTP and tests. 0012 asks for libraries that can reach web.

## Decision

| Concern | Use | Instead of |
|---|---|---|
| Local persistence | SQLDelight | - |
| HTTP (WebDAV, S3, relay plugs) | Ktor client | - |
| Navigation | Navigation Compose with type-safe routes (kotlinx.serialization) | a hand-rolled back stack, which won't scale as an app grows |
| Date and time | kotlinx-datetime | `java.time` (not in `commonMain`) |
| UUID | `kotlin.uuid.Uuid` (stdlib) | benasher44/uuid (superseded) |
| Logging (apps, core) | Kermit; the relay uses Ktor's SLF4J | - |
| Non-secret preferences | multiplatform-settings, never for backend credentials (those use the at-rest path of 0011) | AndroidX DataStore (Android-only) |
| QR scanning | ZXing / zxing-cpp | Google ML Kit |
| Tests | kotest-property in `commonTest`, run on `jvm` | - |

AndroidX / Jetpack libraries are Apache-2 and fine for F-Droid; the "no Google" rule is about
proprietary Google services (GMS, FCM, ML Kit, Maps).

## Rejected

See the right-hand column.

## Consequences

Each new dependency is discussed first, checked for a web path, and recorded here or in a new
decision.
