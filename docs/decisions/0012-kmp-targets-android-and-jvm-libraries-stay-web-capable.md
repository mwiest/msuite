# 0012: KMP targets android and jvm; every library stays web-capable

Date: 2026-07-05. Status: accepted. Formerly OQ-12.

## Context

A web app is planned but deferred. Building a `js`/`wasm` target now costs friction (SQLDelight's
web driver, crypto in JS) for nothing shipped. Going Android-only would lose fast JVM tests for the
simulator (0007) and make the web app a rewrite.

## Decision

- v1 Kotlin Multiplatform targets are `android` and `jvm`. `jvm` runs the test harness.
- No `js`/`wasm` target until the web app is real.
- Every library is chosen so it can reach web later: SQLDelight (sql.js / wasm worker driver; the
  least mature piece), cryptography-kotlin (WebCrypto), Compose Multiplatform (wasmJs), Ktor client
  (fetch engine; watch CORS).
- Platform-only parts stay behind interfaces in the app or platform layer, never in `core`:

| Android | Web later |
|---|---|
| SAF (synced folder plug) | File System Access API; file up/download for the manual plug |
| KeyStore-wrapped root (0011) | Non-extractable WebCrypto key in IndexedDB; weaker than hardware |
| ZXing / zxing-cpp QR scan | `BarcodeDetector` or a JS QR library with `getUserMedia` |
| AlarmManager / WorkManager (reminders, 0014) | Notifications API plus a Service Worker; limited |

## Rejected

- Building the `js` target in v1: pure tax.
- Pure Android: loses JVM tests and a portable `commonMain`.

## Consequences

Adding web is configuration plus platform implementations, not a core rewrite. Every new library
is checked for a web path (see 0020).
