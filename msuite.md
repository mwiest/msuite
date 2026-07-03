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

- Shared design language, UX patterns, and a shared identity + sync engine.
- Each app is useful standalone; the suite is additive.
- **Per-app data stores** (each app owns its op-log + materialized state) — *not* one shared store. Keeps models small, permissions minimal, and each app a distinct F-Droid listing.
- **Cross-app links via URI reference** resolved by intent (e.g. `msuite://notes/<id>`), where natural (task → note, contact → calendar) — never a shared table.
- **Shared identity via federation** (see *Identity, pairing & recovery*): pair once per device; other apps inherit the identity + sync config.

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

### Log retention & snapshots

Merge is a **pure function of per-field state** `(value, HLC, deviceId)` — convergent / state-based. Consequence: the op-log is *transport*, not permanent history. Once a delta batch is synced it collapses into state (LWW merge = take the higher HLC per field), so the local log doesn't grow unbounded and no distributed GC is needed. This holds only while merge stays convergent — so reorder must be an LWW **position field**, not replayed move-ops (see OQ-4).

- **Batches are immutable, content-addressed, and typed** (`delta` | `snapshot`), named so a client can reason about them without decrypting (`deviceId + HLC-range + hash`) — the naming scheme is defined in OQ-3.
- **Deletes are tombstones:** `deleted` is an ordinary LWW field with its own HLC, carried in state and in snapshots — no special GC. A stale edit with a lower HLC can't resurrect a deleted record. Tombstone true-removal is deferred (a cheap, slowly-growing graveyard).
- **Remote growth is bounded by periodic full-state snapshots** (encrypted; a snapshot is just a batch that sets every field to its winning `value@HLC`). A snapshot *subsumes* the batches it folds, so those can be pruned **without a membership list** — a long-offline device merges `local ⊕ latest snapshot` and is still correct. **No device cursors / high-water marks** (a dead device's stale cursor would block GC forever).
- **v1 builds none of this:** retain everything (a validation todo log is tiny). Only the door-openers ship now — immutable/content-addressed/typed batches and per-field HLC in state — so snapshots slot in later with no format change.

The hard, membership-dependent version of GC only returns with **non-convergent** data (counters, true co-editing) — parked behind the same CRDT door.

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

### Identity, pairing & recovery

No accounts — identity is a keypair.

- **New device:** join via **QR pairing** (scan an existing device to receive the key). **Copy/paste recovery key** is the fallback for camera-less or all-devices-lost cases. A lost key with no recovery = data gone, by design.
- **New app on an already-paired device (federation):** each app is standalone with its own local identity by default, but on first run can **import** the shared msuite identity + backend config from an already-installed sibling — a user-consented handoff via a confirmation screen. Result: *pair once per device*; siblings inherit. No mandatory hub app; apps degrade gracefully with zero siblings installed.
- Identity is sourced behind an interface (`IdentityProvider` / `SyncConfig`) so it can come from local keygen (v1, single app) or a sibling (suite) without touching app code — same "keep the door open behind one interface" discipline as LWW→CRDT. Federation itself ships with app #2; v1 only needs the interface.

**Consequences to hold:**
- Identity is *copied* into each app sandbox, so key rotation/revocation must fan out to every app copy (see OQ-8).
- Because the key is shared across sandboxes it **cannot** be a non-exportable Android KeyStore key — you hold exportable key material, encrypted at rest (see OQ-11).
- A `signature`-level permission can harden the handoff *if* F-Droid signing allows a shared cert across your apps; the **user-consent dialog is the real gate** regardless.

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

Stable IDs so we can tackle these one by one; numbering is kept stable as items resolve. OQ-3 is expensive to change later and should be resolved before writing much code; the rest can be settled incrementally.

- **OQ-1 (one identity/store vs. per-app) is resolved** — per-app data stores + shared identity via federation; see *Identity, pairing & recovery* and *Coherence across apps*.
- **OQ-2 (op-log compaction / GC) is resolved** — convergent LWW state means the log is transport, not history; remote growth is bounded by membership-free snapshots; deletes are LWW tombstones. See *Log retention & snapshots*.

### Architecture forks (resolve early — costly to retrofit)

- **OQ-3 — The "dumb store" abstraction leaks with >1 writer; define the plug contract.**
  Concurrent writes resolve differently per backend: Syncthing makes `.sync-conflict` files, S3/WebDAV clobber last-write-wins. That's a *second merge layer below the app's merge layer*. Also "changes since X" on a dumb blob store means list-and-diff, whose cost grows with total batch count; a real relay can offer a cursor, a folder/S3 cannot — so adapters are **not** capability-equivalent. Pin the interface contract: I'd propose "append-only put + list-by-prefix + get," with a naming scheme that makes *every* backend effectively append-only. That scheme must carry the plaintext markers the retention design needs — **immutable, content-addressed, typed** batches (`deviceId + HLC-range + type(delta|snapshot) + hash`, never overwrite) so a client can enumerate and prune without decrypting (see *Log retention & snapshots*). Let the relay optionally accelerate enumeration. (Absorbs the old "minimal plaintext metadata a relay needs" question.)

### Merge correctness

- **OQ-4 — Reorder-within-a-list is in v1 and LWW handles it badly.**
  Concurrent reorders under LWW-per-field produce duplicate positions, interleaving, or lost moves — arguably harder than notes-body editing. Decide for v1: drop reorder (fixed sort by created/due), or design the position encoding (fractional indexing) deliberately and accept it's where LWW's limits first bite. **Constraint from the retention design:** whichever way, reorder must stay an LWW **position field**, not replayed move-ops — move-ops would make merge non-convergent and reintroduce the hard version of OQ-2 (*Log retention & snapshots*).

- **OQ-5 — HLC does not protect LWW from a device with a wrong clock.**
  HLC bounds drift *relative to messages already seen*; LWW still resolves "latest wall-clock wins." A device whose clock is fast wins every conflict regardless of true causality, and in a rarely-syncing suite it can silently dominate. Losing writes vanish with no explanation. State the actual guarantee HLC gives, and decide whether silent per-field loss is acceptable (fine for `done`, bad for a notes body — app #2).

- **OQ-6 — Schema / op migration across an encrypted append-only log.**
  Server can't read ops, so *all* migration is client-side replay. When a field is renamed/removed, you replay long history (with ops referencing dead fields) into the new model, while devices on different app versions may write incompatible ops concurrently. "Versioned data format" is noted; the replay-under-migration story is not.

- **OQ-7 — Merge/HLC test harness is the real product and is currently absent.**
  Concurrent-edit correctness passes manual two-device testing and fails on the third edit. Need a simulated-clock / simulated-partition property-test harness — arguably before build-order step 5.

### Crypto, identity & privacy

- **OQ-8 — One shared keypair → no revocation, no forward secrecy.**
  QR-pairing copies the private key, so all devices share one group key. A lost/stolen device can't be revoked without rotating the key everywhere (very hard with a dumb, membership-less server), and one key compromise exposes all past *and* future ciphertext on the relay. **Federation widens this:** the key is also copied across sibling *apps* on each device, so rotation must fan out to every app sandbox too. Write down the threat model even if the v1 answer is "accepted."

- **OQ-9 — Relay metadata leakage.** Even with E2E, the relay sees object sizes, write timing, device IDs, frequency, and whatever the naming scheme reveals. For a privacy-wedge product, state explicitly what the relay can infer.

- **OQ-10 — Crypto library choice is load-bearing and unstated, and brushes the no-Google rule.**
  "Stay in Kotlin" + KMP shrinks vetted-crypto options. Tink is mature but *is* Google (FOSS/F-Droid-legal, against the spirit of "no Google deps" — needs an explicit ruling); libsodium bindings pull native code. Name the library + AEAD/KDF scheme now, not at step 6. Don't roll your own.

- **OQ-11 — Recovery-key UX contradicts "low-friction."**
  "Write down 24 words or lose everything" is exactly the friction the philosophy warns against, and with "no mandatory backend" the realistic outcome is users losing data and blaming the app. Principled stance is fine, but weigh the product risk. (Related: encryption **at rest on device** — SQLCipher vs. rely on OS sandbox.)

### Scope & product

- **OQ-12 — KMP `js` target while web is deferred = tax for nothing.**
  `core/storage` is annotated `js` but web is deferred; KMP + SQLDelight JS/wasm + crypto-in-JS is ongoing friction against an unshipped target. Options: go pure Kotlin/Android now and extract KMP later, or at least drop `js` from v1 targets.

- **OQ-13 — Is "suite coherence" a user-visible wedge, or an engineering aesthetic?**
  Bring-your-own-backend E2E sync is well-trodden (Joplin, Standard Notes, KeePass, Obsidian+Syncthing). The novel claim — one shared sync layer across a suite — is *developer*-facing. Users install app #2 because it's good and pairs once, not because sync internals match. The real wedge is likely "**pair once, all msuite apps sync**" + consistent feel — now delivered mechanically by federation (pair once per device; see *Identity, pairing & recovery*). Remaining open part: is that enough of a wedge, or is per-app quality what actually drives adoption? Don't over-invest in suite infra before a single app earns its keep.

- **OQ-14 — Reminders/notifications strategy (deferred, but load-bearing).**
  A ToDo without reminders barely validates the *product*, and reminders on Android-without-FCM is itself hard (exact-alarm permission Android 12+/14, OEM battery-killers). v1 validates the tech, not adoption — fine, but be explicit that it tests ~0% of "will anyone adopt app #2." Strategy: AlarmManager/WorkManager, OEM caveats.

- **OQ-15 — Solo-maintainer scope realism.**
  Infra + multiple apps + relay + F-Droid + crypto, unfunded and solo, front-loads maximum cost before any single app ships. Consider what the cheapest path to a *product* signal is (possibly a local-only app first) vs. the current infra-first sequence.
