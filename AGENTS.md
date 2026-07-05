# AGENTS.md — working in the msuite repo

Operational guide for an agent making changes here. For *why* the design is the
way it is, read the design docs below.

## Orientation — authoritative docs

| Doc | What it holds | Read it when |
|---|---|---|
| `msuite.md` | Vision, architecture, and the **decision log** (`OQ-1–15`, all resolved) — rationale, rejected alternatives, accepted tradeoffs, open doors | You need the *why*, or are tempted to change a settled decision |
| `msuite-v1.md` | The v1 build plan — `core` responsibilities, the ToDo feature set, and the 9-step de-risking sequence | Planning or sequencing v1 work |
| `web/README.md` | The deferred web app | Anything web (it's out of scope for now) |
| `AGENTS.md` | This document - hold general rules of working | At session start |

Convention: the decision log in `msuite.md` is **authoritative for rationale**;
design sections stay terse and point to it. Don't duplicate rationale here.

## Current status

Planning phase — **no application code yet** (repo is design docs only). v1 is a
**ToDo app whose real purpose is tech validation** of the shared sync + E2E +
pairing layer. It is a **proof-of-concept, not a product**: success = a working
demonstration of the architecture + UX, *not* adoption.

## Hard constraints (non-negotiable — do not violate without an explicit decision)

- **No proprietary Google deps.** No GMS / FCM / ML-Kit / Maps — must stay
  F-Droid-clean. (E.g. QR scanning = ZXing/zxing-cpp, **never** ML Kit.)
  *Clarification:* **AndroidX/Jetpack** (Compose, Material 3, Navigation, …) is
  Apache-2 and F-Droid-fine — it is **not** what this rule excludes.
- **FOSS, local-first, no accounts, no mandatory backend.** Identity is a shared
  symmetric secret; sync is optional and user-configured.
- **Stay in Kotlin; no native `.so` in v1.** No SQLCipher, no libsodium yet —
  deferred behind interfaces (see OQ-10/OQ-11). Adding native code needs a
  decision.
- **Keep libraries web-capable (KMP), but don't build the `js`/`wasm` target in
  v1.** v1 targets = `android` + `jvm` only (`jvm` runs the test harness). See
  OQ-12.
- **All merge stays client-side.** Backends are dumb, interchangeable,
  E2E-encrypted stores. Never introduce a second merge layer below the app's.
- **Boundary rule:** `apps/*` depend on `core/*`, **never on each other**.
- **Don't reopen resolved decisions (`OQ-1–15`) without cause** — check the
  decision log first; each records why alternatives were rejected.
- **No telemetry.** No analytics or crash-reporting SDKs — a privacy guarantee.
  (If ever needed: ACRA, FOSS, strictly opt-in — nothing else.)

## How to work
- Always check whether any docs need to be updated after a change.
- Always check design/architecture after a change and suggest improvements, e.g. to avoic duplicate code created, potential security risks added, etc.
- Never git commit, unless explicitly asked.
- Whenever we take decisions, document them in the matching authoritative doc.
- When adding new dependencies, always discuss and later document the decision. We want to keep dependencies lean and consistent.
- Use TDD and cover every feature or change with at least one test case (unless it's arguably not applicable, then state why)

## Intended structure (once code exists)

Monorepo, Gradle multi-module. Full module map + build rationale in
`msuite.md` → *Project structure & build*. In brief:

- `build-logic/` — convention plugins (main defense against monorepo rot)
- `gradle/libs.versions.toml` — single version catalog
- `core/{model,storage,sync,crypto,design}` — shared, small modules by boundary
- `apps/{todo,notes,…}` — Android app modules (`com.msuite.<app>`)
- `server/` — the dumb relay: single Ktor (Kotlin/JVM) binary, in-repo so wire-format never drifts. Coded **reflection-averse** (CIO engine + programmatic config, filesystem storage, manual DI) to keep a GraalVM-native build cheap and deferred
- `web/` — deferred

## Chosen / rejected libraries (quick reference; rationale in OQ-10/OQ-12)

| Concern | Use | Do **not** use |
|---|---|---|
| Local persistence | SQLDelight (KMP: android, jvm) | — |
| Serialization (batches/snapshots) | kotlinx.serialization + ProtoBuf (see *Batch encoding*) | Square Wire (unneeded toolchain), JSON as primary (size) |
| Crypto | cryptography-kotlin (whyoleg) — AES-256-GCM, HKDF-SHA-256, SHA-256 | Tink (Google), libsodium (native) — for now |
| HTTP (WebDAV/S3/relay plugs) | Ktor client | — |
| Relay server | Ktor server (CIO engine), reflection-averse for GraalVM | Netty engine + HOCON module loading (reflection); JDBC/native SQLite storage |
| QR scan | ZXing / zxing-cpp | Google ML Kit |
| UI toolkit | Compose Multiplatform + Material 3 (`core/design` in `commonMain`) | androidx Compose (Android-only — blocks commonMain design) |
| Navigation | Navigation Compose, type-safe routes (kotlinx.serialization) | hand-rolled back stack for a growing app |
| Dependency injection | Manual constructor injection + per-app composition root (add Koin in the app layer only if wiring grows) | Hilt/Dagger (Android-only, Google); any reflection DI in the relay |
| Date/time | kotlinx-datetime | java.time (Android-only, not commonMain) |
| UUID | `kotlin.uuid.Uuid` (stdlib) | benasher44/uuid (superseded) |
| Logging (client/core) | Kermit (relay uses Ktor's SLF4J separately) | — |
| Settings (non-secret prefs) | multiplatform-settings — **never** backend creds (those use the OQ-11 at-rest path) | AndroidX DataStore (Android-only) |
| Tests | kotest-property (`commonTest`, runs on `jvm`) | — |
| Lint / format | Spotless (ktlint) for formatting + detekt for static analysis (wired in `build-logic`, CI-gated) | — |

**Don't roll your own crypto** — compose primitives from cryptography-kotlin only.

## Build / test / run

**SDK levels:** `minSdk` = **26** (Android 8.0); `compileSdk`/`targetSdk` = **latest stable**.

**JVM:** Gradle toolchain **JDK 21** (LTS) to build; Kotlin `jvmTarget` = **17**.

**License:** GPLv3 across the whole repo (including `server/`) — the relay's GPLv3-vs-AGPLv3 "network service" loophole is knowingly accepted for simplicity.

**CI:** GitHub Actions (repo is `github.com/mwiest/msuite`) — runs the kotest merge/HLC harness on `jvm`, Spotless + detekt, debug-APK assembly, relay-jar build. This is a dev quality gate, **not** the release pipeline (F-Droid does its own reproducible builds from metadata). **Renovate** keeps deps current (first-class `libs.versions.toml` support).

No build system is wired up yet. Once it is, document the real commands here
(Gradle module builds, per-app `applicationId`, `kotest-property` merge/HLC
simulator in `commonTest` on the `jvm` target — OQ-7). Per-app release tags use a
prefix (`todo-v*`); `versionCode` is monotonic per `applicationId`.

## Dev environment

- **Windows.** Primary shell is PowerShell; a Bash (Git Bash / POSIX) tool is
  also available — use each with its own syntax.
- Use \n for line breaks and generally keep everything as UNIX compatible as possible.
