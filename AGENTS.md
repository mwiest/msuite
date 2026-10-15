# msuite - Agent Notes

## Project Overview

msuite is a suite of FOSS, local-first Android apps (ToDo first, then Notes, Contacts, Calendar)
that share design, an identity, and an optional end-to-end-encrypted sync layer through backends
the user picks. No accounts, no server we host. It is a **proof of concept, not a product**:
success is a working demonstration of the architecture and UX, not adoption. What it is and does:
`docs/product.md`. Where v1 stands: `docs/plans/v1-todo.md`.

## Tech Stack

- Kotlin Multiplatform, Gradle multi-module monorepo. Targets `android` and `jvm` (`jvm` runs the
  tests); the web target is not built yet.
- Android: `minSdk` 26, `compileSdk`/`targetSdk` latest stable (37). Build on JDK 21, emit Java 17
  bytecode.
- Compose Multiplatform with Material 3; shared theme and components in `core/design`.
- Relay: Ktor (CIO engine) on the JVM.

| Concern | Use | Do **not** use |
|---|---|---|
| Local persistence | SQLDelight | - |
| Encoding (batches, snapshots) | kotlinx.serialization ProtoBuf | Square Wire, JSON as primary |
| Crypto | cryptography-kotlin: AES-256-GCM, HKDF-SHA-256, SHA-256 | Tink, libsodium (for now) |
| HTTP (plugs) | Ktor client | - |
| Relay server | Ktor CIO, programmatic config, filesystem storage | Netty, HOCON module loading, JDBC / native SQLite |
| QR scan | ZXing / zxing-cpp | Google ML Kit |
| UI | Compose Multiplatform + Material 3 | AndroidX Compose (Android-only) |
| Drag to reorder | Reorderable (`sh.calvin.reorderable`) | a hand-rolled drag |
| UI text | Compose Multiplatform resources, English and German | Android `strings.xml` |
| Navigation | Navigation Compose Multiplatform (`org.jetbrains.androidx.navigation`), type-safe routes | a hand-rolled back stack |
| Dependency injection | Manual constructor injection, one composition root per app (Koin in the app layer only if wiring grows) | Hilt / Dagger; any reflection DI in the relay |
| Date and time | kotlinx-datetime | `java.time` |
| UUID | `kotlin.uuid.Uuid` | benasher44/uuid |
| Logging | Kermit (the relay uses Ktor's SLF4J) | - |
| Non-secret settings | multiplatform-settings, **never** backend credentials | AndroidX DataStore |
| Tests | kotest + kotest-property in `jvmTest`, logic in `commonMain` | - |
| Format / lint | Spotless (ktlint), detekt | - |

Reasons: decisions 0010, 0012, 0016 to 0020, 0026, 0037, 0038, 0046, 0047, 0063.

## Hard constraints

Not to be broken without a new decision record.

- **No proprietary Google dependencies**: no GMS, FCM, ML Kit, Maps; F-Droid-clean. AndroidX /
  Jetpack (Apache-2) is fine.
- **FOSS, local-first, no accounts, no mandatory backend.** Identity is a shared symmetric secret;
  sync is optional and set up by the user.
- **Kotlin only, no native `.so`** (no SQLCipher, no libsodium yet).
- **Keep libraries web-capable**, but don't build the `js`/`wasm` target yet (decision 0012).
- **All merge stays in the client.** Backends are dumb, interchangeable stores of ciphertext; never
  a second merge layer below the app's (decisions 0003, 0028).
- **Apps depend on `core/*`, never on each other** (`apps/<a>/*` never on `apps/<b>/*`).
  App logic lives in the KMP module `apps/<app>/domain` (no Compose), its UI in
  `apps/<app>/shared`; `apps/<app>/android` is a thin shell (decisions 0037, 0051).
- **Don't roll your own crypto**: compose primitives from cryptography-kotlin only.
- **No telemetry**: no analytics or crash-reporting SDKs (decision 0024).
- Don't reopen a recorded decision without cause: read its "Rejected" section first.

## Style guide

- Use the default Kotlin style guide; Spotless (ktlint) and detekt enforce it.
- Use the latest stable versions of libraries. Every new dependency is discussed first and recorded
  (decision 0020 or a new one); keep dependencies lean and consistent.
- Never hard-code UI text in components; every string ships in English and German, in the same
  commit (decision 0027).
- Platform-only code (SAF, KeyStore, camera, alarms) stays behind interfaces in the app layer,
  never in `core`.

## Building (confirming the change builds)

Needs a JDK 21 installed (the daemon is pinned to it, so `JAVA_HOME` may point at a newer JDK) and
an Android SDK with platform 37. Point at the SDK with `local.properties` (`sdk.dir=...`,
git-ignored) or `ANDROID_HOME`. Shared build settings live in the `build-logic` convention plugins
(`docs/architecture.md` -> Build).

```bash
./gradlew assembleDebug          # all modules + debug APK (apps/todo/android/build/outputs/apk/debug/todo-debug.apk)
./gradlew :server:jar            # relay jar
./gradlew :server:run            # run the relay (placeholder main for now)
```

## Running tests and checks

```bash
./gradlew allTests               # all tests on all KMP targets
./gradlew :core:sync:jvmTest     # sync tests and the merge/HLC simulator
./gradlew spotlessApply          # fix formatting
./gradlew spotlessCheck detekt   # lint, as CI runs it (`check` would add Android lint too)
```

More in `docs/testing.md`.

## Agent collaboration mode

Whenever you need to take a decision that has multiple options, ask me instead of guessing or
assuming. When asking, explain quickly the pros and cons of each option.

Rather do small steps and finish them, instead of trying to build too much at once.

Commit regularly to Git.

Work test-first: cover every feature or change with at least one test, or state why none applies.
When asked only to write tests, do NOT touch non-test code unless explicitly told.

After a change, check the design for problems (duplicated code, security risks, a boundary
crossed) and suggest improvements.

Write comments sparingly, very concise and only when there's a real gotcha for a future reader.
Avoid explaining during-process information or learnings, unless they're really load-bearing.

## Dev environment

Windows. PowerShell is the main shell; a Bash (Git Bash) tool is also available - use each with
its own syntax. Keep files UNIX-style: LF line endings, `./gradlew`.

## Documentation

Every fact lives in one place; elsewhere, link to it. Docs describe what the code does now, plans
describe work in flight, git holds history.

| Where | What |
|---|---|
| `AGENTS.md` (`CLAUDE.md` imports it) | Basics: overview, stack, build, rules, invariants, this map |
| `README.md` | Public front page |
| `docs/product.md` | What msuite is and what the apps do today |
| `docs/architecture.md` | Modules, build, the sync design, identity and pairing |
| `docs/testing.md` | Test commands, the merge/HLC simulator and its invariants |
| `docs/roadmap.md` | Upcoming work, one line each, linking to its plan |
| `docs/decisions/` | One decision per file (`NNNN-slug.md`, see `_template.md`); never edited, only replaced. 0001 to 0015 were OQ-1 to OQ-15 |
| `docs/plans/` | One plan per piece of work in flight (see `_template.md`) |
| `web/README.md` | The deferred web app |
| `mocks/` | Icon drafts |

Rules:

- A change in behaviour updates `docs/product.md` in the same commit; a change in structure
  updates `docs/architecture.md`.
- A decision gets a record in `docs/decisions/` in the same commit.
- Finishing a slice updates its plan's status line and checkbox, with the commit hash.
- When a plan is done, move what lasts into `architecture.md`, `testing.md` or a decision record,
  then delete the plan.
- Write dates as absolute dates.
- Project knowledge goes in the repo, not in an agent's private memory.
