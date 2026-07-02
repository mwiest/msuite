# Msuite — Vision & Architecture

A coherent set of FOSS, local-first mobile apps — small "handy helper" utilities that share design, principles, and storage/sync, so a user of one is likely to adopt the others.

> v1 plan lives in `msuite-v1.md`. Web app is deferred; see `web/README.md`.

## Philosophy

| Principle | Meaning |
|---|---|
| FOSS | Open source, F-Droid-friendly. |
| Local-first | Data lives on device; works fully offline. |
| No registration | No account required. Identity = keypair. |
| No mandatory backend | Developer hosts nothing; sync is optional. |
| Good UX | Simple, low-friction, opinionated defaults. |
| Material 3 | Consistent design system across apps. |
| No proprietary Google deps | No GMS/FCM/ML-Kit/Maps → clean F-Droid publishing. |

**The wedge:** not out-featuring incumbents (impossible solo) but *coherence + a shared, serverless sync layer* across a suite — which no one has nailed.

## Coherence across apps

- Shared design language, UX patterns, and storage/sync layer.
- Each app is useful standalone; the suite is additive.
- Cross-app links where natural (task → note, contact → calendar).

## App roster

First four, rough complexity order: **ToDo → Notes → Contacts → Calendar** (Calendar hardest: recurrence, CalDAV-like).

Further candidates (not only Google clones): habit/streak tracker, expense tracker, password/secrets vault, bookmarks/read-later, recipes, document scanner, journal/diary. Common thread: small data model, local-first is a genuine *selling point* (privacy), pairs with another app.

## Sync architecture (the core innovation)

Fault tolerance + optional cross-device sync **without** a central server the developer must host.

| Decision | Rationale |
|---|---|
| **Dumb server + E2E encryption** | All merge intelligence in the client; server only shuffles ciphertext it can't read → needs no accounts, no app logic, no schema → hostable anywhere. |
| **Op-log, not state-blobs** | Sync moves an ordered log of ops (`set field X = Y @ HLC on device D`), not whole records. |
| **LWW merge for v1** | Last-Writer-Wins: pure Kotlin, simple, good enough when the same field is rarely co-edited offline. |
| **HLC clock** | Hybrid Logical Clock keeps ordering sane despite device clock skew. |
| **CRDT deferred, not rejected** | Only needed for true concurrent co-editing (e.g. shared rich-text). Would require a Rust engine → breaks "stay in Kotlin," so out of scope until justified. |
| **Fully local search** | E2E means no server-side indexing, ever. Fine at todo/notes/contacts scale. |

**Keeping the LWW→CRDT door open:** merge sits behind one `merge(local, remote)` interface; every record carries per-field `(value, HLC, deviceId)`; data format is versioned. Anti-pattern to avoid: storing only final state with no per-field metadata — that permanently forecloses smarter merging.

### Backend "plugs" (user-chosen adapters)

Sync goes through a **backend-adapter interface**. The *user* picks and configures a plug; the developer neither hosts nor offers any service. Because all plugs move only E2E-encrypted op-batches, every backend is a dumb, interchangeable store, and the interface stays open for more.

| Plug | Uses | Setup |
|---|---|---|
| None / manual | Export/import file, local Wi-Fi | None |
| Synced folder | Syncthing / Nextcloud / Dropbox folder (via SAF) | Point at folder |
| WebDAV | Any WebDAV server (Nextcloud, …) | URL + creds |
| Object storage | S3-compatible bucket (R2, B2, MinIO) | Bucket + keys |
| Dumb relay | Bundled `server/` binary, self-host or cheap PaaS | Run binary / deploy |

Self-hosted and cloud relay are the **same binary**. Free/cheap host options for those who want them: Cloudflare (Workers/R2/Durable Objects), Oracle Always-Free VM, PikaPods/Railway/Koyeb.

### Identity & key recovery

No accounts — identity is a keypair. New devices join via **QR pairing** (scan an existing device to receive the key); **copy/paste recovery key** is the fallback for camera-less or all-devices-lost cases. A lost key with no recovery = data gone, by design.

## Project structure & build

**Monorepo**, Gradle multi-module. Practical for F-Droid: each app is a module with its own `applicationId`, built via the recipe's `subdir`, tracked by a per-app tag prefix (`todo-v*`).

| Monorepo keeps in sync | Stays per-app (F-Droid) |
|---|---|
| Dependency versions (one catalog), build config (convention plugins), shared `core` | Release versions & git tags, `versionCode` (monotonic per `applicationId`), each app = distinct listing |

```
msuite/                        (one git repo)
├─ build-logic/                convention plugins — main defense against monorepo rot
├─ gradle/libs.versions.toml   single version catalog
├─ core/
│  ├─ model/                   shared domain types + op definitions
│  ├─ storage/                 op-log, local persistence   (KMP: android, jvm, js)
│  ├─ sync/                    HLC, LWW merge, backend-plug interface + adapters
│  ├─ crypto/                  E2E, keypair, QR pairing
│  └─ design/                  Material 3 theme, shared Compose components
├─ apps/{todo,notes,...}/      Android app modules (com.msuite.<app>)
├─ web/                        deferred — reuses core/*   (see web/README.md)
└─ server/                     the dumb relay
```

- `core` split into small modules to enforce boundaries; apps depend only on what they need.
- `server` lives in-repo so client/relay wire-format never drifts.
- **Boundary rule:** apps depend on `core/*`, never on each other.

## Non-goals

- Replicating heavyweight apps (Docs, Photos).
- Running/maintaining any backend or hosted service for users.
- Accounts, logins, or proprietary SDKs.
- Monetization — solo hobby project, unfunded. Donations optional, never required.

## Open questions

- One shared data format across apps, or per-app schemas?
- Encryption **at rest on device** (e.g. SQLCipher), or rely on OS sandbox?
- Minimal plaintext metadata (if any) a relay needs for incremental "changes since X" routing without breaking E2E.
- Notifications/reminders strategy (AlarmManager/WorkManager; OEM battery-killer caveats) — deferred.
