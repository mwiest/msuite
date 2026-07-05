# Msuite — Vision & Architecture

A coherent set of FOSS, local-first mobile apps — small "handy helper" utilities that share design, principles, and storage/sync, so a user of one is likely to adopt the others.

> v1 plan lives in `msuite-v1.md`. Web app is deferred; see `web/README.md`.

## Philosophy

| Principle | Meaning |
|---|---|
| FOSS | Open source, F-Droid-friendly. |
| Local-first | Data lives on device; works fully offline. |
| No registration | No account required. Identity = a shared symmetric secret. |
| No mandatory backend | Developer hosts nothing; sync is optional. |
| Good UX | Simple, low-friction, opinionated defaults. |
| Material 3 | Consistent design system across apps. |
| No proprietary Google deps | No GMS/FCM/ML-Kit/Maps → clean F-Droid publishing. |

**The thesis (this is a proof-of-concept, not a product play):** not out-featuring incumbents (impossible solo, and not the goal) but proving that *coherence + a shared, serverless sync layer* across a privacy-first FOSS suite with good UX is buildable by a solo dev — which no one has nailed. Success = a working demonstration of the architecture + UX, **not adoption** (see *Non-goals*).

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
| **HLC clock** | Hybrid Logical Clock preserves *causal* order despite clock skew (concurrent edits still resolve LWW-by-wall-clock — see OQ-5). |
| **CRDT deferred, not rejected** | Only needed for true concurrent co-editing (e.g. shared rich-text). Would require a Rust engine → breaks "stay in Kotlin," so out of scope until justified. |
| **Fully local search** | E2E means no server-side indexing, ever. Fine at todo/notes/contacts scale. |

**Keeping the LWW→CRDT door open:** merge sits behind one `merge(local, remote)` interface; every record carries per-field `(value, HLC, deviceId)`; data format is versioned. Anti-pattern to avoid: storing only final state with no per-field metadata — that permanently forecloses smarter merging.

### Batch encoding

Op-batches and the export snapshot serialize via **kotlinx.serialization + ProtoBuf** (KMP-native, behind an encoder interface in `core/model`). Rationale: compact binary keeps the OQ-9 size leak small; proto **field numbers double as the OQ-6 stable opaque IDs** (renames are non-events, add/remove/type-change is the only migration surface); one encoder covers android + jvm (+ web later). We don't rely on protobuf's own unknown-field retention — OQ-6 forward-compat is modelled explicitly as first-class `(id, bytes, HLC, deviceId)` state entries — so no `.proto` schema toolchain (e.g. Square Wire) is needed. **Determinism** (same logical batch → identical bytes, for OQ-3 idempotent retry and reproducible tests) requires canonical field/map ordering, which we control. A human-readable JSON *debug* view can be added behind the same interface without changing the primary format.

### Log retention & snapshots

Merge is a **pure function of per-field state** `(value, HLC, deviceId)` — convergent / state-based. Consequence: the op-log is *transport*, not permanent history. Once a delta batch is synced it collapses into state (LWW merge = take the higher HLC per field), so the local log doesn't grow unbounded and no distributed GC is needed. This holds only while merge stays convergent — so reorder must be an LWW **position field**, not replayed move-ops (see OQ-4).

- **Batches are immutable, content-addressed, and typed** (`delta` | `snapshot`), named so a client can reason about them without decrypting (`type + deviceId + HLC-range + hash`) — the naming scheme is pinned under *Backend "plugs"* → *Plug contract*.
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

The relay is a **single Ktor (Kotlin/JVM) binary** — same artifact for self-host and cloud — reusing the OQ-3 name-grammar + contract types from `core` so the wire never drifts. Free/cheap host options: Oracle Always-Free VM, PikaPods / Railway / Koyeb / fly.io. **Cloudflare needs no relay** — R2 is S3-compatible, so those users point the *Object-storage plug* at R2 directly (a JVM binary can't run on Workers anyway).

**Reflection-averse by design (keeps the GraalVM-native door cheap):** the relay avoids runtime reflection so a future GraalVM native image (smaller, fast cold-start, low memory) stays config-not-rewrite — Ktor **CIO** engine + programmatic `embeddedServer` (no HOCON module-by-FQN loading), **filesystem** blob storage (no JDBC / native SQLite), manual wiring (no reflection DI), and kotlinx.serialization (compile-time codegen — already chosen). Any residual reflection is covered by GraalVM reachability metadata + a tracing-agent pass in CI. The native image itself is a **deferred optimization** — a plain JVM jar validates step 8; go native only if hosting footprint bites.

#### Plug contract (resolves OQ-3)

Every plug implements one narrow, append-only interface; **all merge stays in the client**. The problem this closes: a dumb store with >1 writer otherwise sneaks in a *second merge layer below the app's* — Syncthing writes `.sync-conflict` files, S3/WebDAV clobber last-write-wins.

**Interface** — v1 builds `put` / `list` / `get`; `delete` and a capability descriptor arrive later, with snapshots/relay.

| Op | Contract |
|---|---|
| `put(name, bytes)` | **Create-only, atomic publish.** A name returned by `list` always points to a complete, immutable object. (SAF: temp-write + rename; S3/WebDAV: PUT is atomically visible; relay enforces.) |
| `list(prefix)` | Enumerate names only, no bodies. |
| `get(name)` | Fetch bytes; reader may re-verify the body against the name's hash. |
| `delete(name)` | *Later, capability-gated* — used by snapshot pruning on stores that support it. |

**Naming defeats the second merge layer.** Each batch's name is derived from its metadata plus a hash of its ciphertext:

```
v{fmt}_{type}_{deviceId}_{hlcLo}_{hlcHi}_{hash}
```

- `v{fmt}` — naming-scheme version (migration door). `type` — `d` (delta) | `s` (snapshot). `deviceId` — **one writer per batch**. `hlcLo..hlcHi` — the HLC span covered (drives snapshot subsumption). `hash` — over the **ciphertext**, giving content-addressing + integrity + dedup + idempotent put.
- Charset `[A-Z0-9_]`, `_`-separated (no `:` or `/`) — valid as an S3 key, a WebDAV path segment, and a FAT/ext4/SAF filename, well under 255 chars.
- **Binary fields are encoded to fit the charset:** `hash` (SHA-256) and `deviceId` are encoded as **unpadded uppercase base32** (RFC 4648 `[A-Z2-7]` ⊂ `[A-Z0-9]`; fixed lengths → no `=` padding). Full SHA-256 → 52 chars, so no truncation needed. All-uppercase also prevents collisions on **case-insensitive** stores (FAT/exFAT/Windows, some WebDAV servers), where two names differing only by case would clash.

Because distinct writes get distinct names, two devices writing concurrently produce two objects — never a clobber, never a `.sync-conflict`. The storage layer never merges; the app's LWW merge is the only merge layer. `put` is never issued as an overwrite, so create-only and overwrite backends behave identically. **Retry-idempotency:** a batch's ciphertext is computed *once* (its OQ-10 random salt fixed at creation, bytes cached) so a retried `put` re-sends identical bytes to the same name. Note the salt means two devices with semantically identical content still produce *different* objects — fine for merge (LWW converges) and good for privacy (no cross-device dedup; see OQ-9).

**Per-plug realization** — the same flat set of immutable, uniquely-named ciphertext blobs everywhere, differing only in physical container and atomic-create technique:

| Plug | One batch is… | Atomic publish | Create-only | `list(prefix)` |
|---|---|---|---|---|
| Synced folder (SAF) | a file, filename = name | temp-write + rename; temp uses a non-matching name (`.tmp_*`) so `list` never sees it; the sync client (Syncthing/Nextcloud/Dropbox) also rename-on-complete across devices | unique by construction; a retry re-sends identical bytes | list dir children, filter prefix |
| WebDAV | a resource, path segment = name | server `PUT` atomically visible, or `PUT`-temp + `MOVE` for stubborn servers | `If-None-Match: *` | `PROPFIND Depth:1` (minimal props) + prefix filter |
| Object storage (S3/R2/B2/MinIO) | an object, key = name | **native** — atomic put + strong read-after-write | `If-None-Match: *` (else moot — identical-content retry is harmless) | `ListObjectsV2(prefix)` — native |
| Dumb relay | a file on disk (filesystem storage — keeps GraalVM-native easy; a pure-Kotlin KV is an option) | relay **enforces** it (temp+rename) | relay rejects an existing name and verifies body-vs-`hash` | relay returns names by prefix (may add a cursor later — the only backend that can) |

In every row, distinct names mean concurrent writers produce distinct blobs — no clobber, no `.sync-conflict`, no server-side merge or lock. `delete` (capability-gated) maps to file delete / WebDAV `DELETE` / `DeleteObject` / relay delete on all four, which is what makes snapshot pruning viable on real backends (unlike the manual plug, which relies on client-side compaction instead). Readers may re-verify a fetched body against the name's `hash` as belt-and-suspenders against truncation.

**Two plug shapes over the same interface:**

- **Multi-object store** — synced folder via SAF, later S3/WebDAV/relay. Many append-only objects; *this is where concurrent writers actually occur*. Deltas accumulate; snapshots + `delete` prune them later (see *Log retention & snapshots*).
- **Single-file export/import** — the "None / manual" plug, **v1's shipping plug**. A size-1 keyspace holding one full-state snapshot. Each export re-emits a fresh compacted snapshot, so a no-delete backend never grows: **client-side compaction** removes the need for a `delete` primitive here. (This snapshot is just "serialize materialized per-field state to one file" — the cheap door-opener, *not* the deferred membership-free pruning machinery OQ-2 parks.)

**Enumeration is list-and-diff.** "Changes since X" = `list(prefix)` minus locally-tracked ingested names → `get` the rest. Cost is O(objects) until snapshots prune — no server cursor anywhere in the design. A relay may add one later *only if* it proves necessary; the naming scheme need not change for that.

**Adapters are not capability-equivalent** (delete, and any eventual cursor). A capability descriptor is added when the first plug needs to differ; v1's single plug needs none.

### Identity, pairing & recovery

No accounts — identity is a single **256-bit symmetric root secret** shared across a user's devices (and, via federation, sibling apps). Not an asymmetric keypair: the data path and pairing are symmetric-only in v1 (see OQ-8, OQ-10).

- **New device:** join via **QR pairing** — the QR carries the root secret itself (+ a checksum), scanned by the new device. Physical-trust model: whoever captures the on-screen QR while it's shown gets full access (see OQ-8). **Copy/paste recovery key** is the fallback for camera-less or all-devices-lost cases. A lost key with no recovery = data gone, by design.
- **New app on an already-paired device (federation):** each app is standalone with its own local identity by default, but on first run can **import** the shared msuite identity + backend config from an already-installed sibling — a user-consented handoff via a confirmation screen. Result: *pair once per device*; siblings inherit. No mandatory hub app; apps degrade gracefully with zero siblings installed.
- Identity is sourced behind an interface (`IdentityProvider` / `SyncConfig`) so it can come from local keygen (v1, single app) or a sibling (suite) without touching app code — same "keep the door open behind one interface" discipline as LWW→CRDT. Federation itself ships with app #2; v1 only needs the interface.

**Consequences to hold:**
- Identity is *copied* into each app sandbox, so key rotation/revocation must fan out to every app copy (see OQ-8).
- Because the key is shared across sandboxes it **cannot** be a non-exportable Android KeyStore key — you hold exportable key material, encrypted at rest under a *device-local* KeyStore-wrapped blob (see OQ-11).
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
│  ├─ storage/                 op-log, local persistence   (KMP: android, jvm — js dropped, OQ-12)
│  ├─ sync/                    HLC, LWW merge, backend-plug interface + adapters
│  ├─ crypto/                  E2E, symmetric root secret, QR pairing
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
- Adoption / user growth as a success metric — this is a proof-of-concept; success is a working demonstration of the architecture + UX. Reminders and other app-polish features are deferred on that basis (they exercise none of the shared-layer thesis), not scheduled against a growth plan.

## Decisions & open questions

OQ numbers stay stable as items resolve. **OQ-1–15 are all resolved.**

Three were resolved early and folded directly into the architecture above — no separate block below:

- **OQ-1** (one store vs. per-app) → per-app data stores + shared identity via federation — see *Coherence across apps*, *Identity, pairing & recovery*.
- **OQ-2** (log compaction / GC) → merge is convergent, so the log is transport, not history; remote growth bounded by membership-free snapshots; deletes are LWW tombstones — see *Log retention & snapshots*.
- **OQ-3** (dumb-store plug contract) → append-only `put`/`list`/`get` with immutable, content-addressed, typed names, so distinct writes never collide and no second merge layer forms — see *Plug contract*.

### Merge correctness — OQ-4–7 (resolved)

- **OQ-4 (reorder within a list) is resolved — position is one LWW fractional-index field.**
  Reorder ships in v1 as an ordinary per-field LWW value, honoring the retention constraint (a position *field*, never replayed move-ops — move-ops are non-convergent and would reintroduce the hard OQ-2 GC). Design:
  - Each item carries `position` = an LWW fractional-index **string** (standard `keyBetween` scheme). A list renders sorted by `(position, itemId)`.
  - Append = `keyBetween(lastKey, null)`; move X between A,B = set `X.position = keyBetween(A.position, B.position)` with a fresh HLC (one field write).
  - **Jitter:** generated keys get a short random suffix, so two devices inserting into the same gap concurrently produce *distinct* keys, not a collision.
  - **Tiebreak:** identical keys (rare, with jitter) fall back to `itemId` (a stable UUID) → total order is deterministic on every device regardless of merge order.
  - Because `position` is a normal LWW field, merge stays convergent and snapshots still subsume deltas — no format or retention impact.
  - **Residual anomalies are cosmetic, not corrupting** — the exact bound the OQ-7 harness should assert: concurrent move of the *same* item → higher-HLC wins, other lost (per-field LWW, see OQ-5); concurrent moves of *different* items into overlapping ranges → deterministic but possibly surprising interleave; same-gap inserts → distinct keys, no tie. No duplicate positions, no lost/duplicated items, no crash.
  - **v1 scope:** manual order only (no display-only sort modes). Key **rebalancing** (repeated same-gap inserts grow key length) is deferred — unnecessary at todo scale, and when needed it's just a batch of LWW `position` writes, no format change.

- **OQ-5 (wrong-clock / silent LWW loss) is resolved — accepted for v1, pure HLC.**
  **What HLC guarantees:** (1) *causality* — if edit A was visible when B was written (A → B), then `HLC(A) < HLC(B)`, so causally-ordered edits merge in intended order (the common case where you did sync recently); (2) `pt` tracks the max wall-clock actually observed, so it can't diverge purely logically and stamps stay roughly wall-clock-meaningful; (3) `(pt, c, deviceId)` is a deterministic total order → every device converges on the same winner.
  **What it does NOT give:** any *correct* ordering of **concurrent** writes (neither device saw the other). LWW linearizes those by wall clock, so a fast-clock device wins every concurrent conflict — and in a rarely-syncing suite, edits between syncs *are* concurrent by definition, so the conflicts that occur are predominantly the class HLC can't order. It linearizes concurrent writes but cannot make that ordering *correct* — no timestamp scheme can; only a partial-order clock (version vectors) or a non-LWW per-field merge would.
  **v1 decision — accept silent per-field loss.** No ToDo field is catastrophic to lose (`done` re-toggles, title/notes are retyped, scalars are scalars). Conflict *detection* would need per-field version vectors, whose per-device metadata + dead-device pruning **undo OQ-2's membership-free retention** — not worth paying in v1. The loser is discarded and, after compaction, unrecoverable. This is a bounded, documented acceptance.
  **v1 keeps pure HLC — no max-drift clamp.** A pathologically wrong clock (set far in the future) therefore dominates permanently; also accepted for v1. The clamp — peers reject a batch whose `pt` exceeds local `now()` by more than a bound (~1h–1d) — is a known, cheap mitigation deferred behind the ingest path: a check at merge time, no data-format change, addable the day a bad clock shows up in the wild.
  **Door for app #2 (a notes body, where LWW loss *is* unacceptable):** a per-field merge strategy behind `merge()` (CRDT/3-way for that one field) — the already-parked CRDT door, not a clock fix. OQ-4's `position` field inherits this same accepted loss.

- **OQ-6 (schema / op migration) is resolved — no history replay; stable IDs + read-time upcasters + opaque forward-compat.**
  OQ-2 removes the scary premise: merge is a pure function of per-field state, the log is transport not history, so there is **no long history to replay**. Migration reduces to (a) local materialized-state migration and (b) cross-version batch handling at *read time* (batches are immutable — OQ-3 — so never rewritten).
  - **Stable opaque IDs** identify fields / ops / record-types; source names are display-only. **Renames are non-events** (zero wire/migration impact); the migration surface is just add / remove / type-change. Removed IDs are **reserved forever** (never reused) so stale ops for them are dropped at ingest; type change = remove-old-ID + add-new-ID with a derivation.
  - **Read-time upcasters:** each batch carries a **data-model version in its (decrypted) header** — never in plaintext, so no OQ-9 leak. Ingest = decrypt → run ops through a pure `upcast(vK→vK+1)` chain to the app's current model version → merge. Old / long-offline data and old snapshots upcast on read; snapshots (OQ-2) bound how much old-format data lingers, so the upcast surface is *in-transit deltas + materialized state* — bounded.
  - **Forward-compat via opaque pass-through:** per-field state is ID-keyed and **retains unknown field IDs opaquely** `(id, bytes, HLC, deviceId)`. An old app materializes only known IDs into its typed model but carries unknowns through merge and into any snapshot it writes → it stays fully functional (incl. snapshotting) and **never erases newer fields**. Implementation: typed columns for known fields + a small opaque side-table for unknowns — a natural extension of the per-field-metadata store OQ-2 already requires.
  - **Local state migration** is ordinary versioned DB migration (SQLDelight).
  - **v1 builds only the seams** (single version → no migration yet): stable IDs in the encoding, opaque-unknown retention in the state store, a data-model version header (=1), and an `upcast` hook that is identity for now. Truly complex, non-pure migrations (rare) are handled case-by-case.

- **OQ-7 (merge/HLC test harness) is resolved — deterministic multi-device simulator, property-based, Layer 1 gates step 4.**
  A seeded in-memory simulator of N virtual devices, each with a controllable clock (offset / drift / mid-run jump), exchanging batches over a transport that can partition, reorder, duplicate, and drop. A generated **schedule** (`Op` / `Tick` / `Push` / `Pull` / `Deliver`) runs, then a quiescence phase (connect all, sync to fixpoint) precedes assertions. Property-based with shrinking (**kotest-property**, KMP `commonTest`) plus named regression scenarios for the known-tricky cases. **Determinism is required** (seed everything) — which forces OQ-4's `keyBetween` jitter RNG to be *injectable/seedable*, not internal.
  **Invariants asserted:**
  - *Convergence:* (1) strong eventual consistency — byte-identical materialized state on all devices after quiescence; (2) order-independence of delivery; (3) idempotent delivery.
  - *LWW / clock (OQ-5):* (4) single winner per field = max `(pt, c, deviceId)`, computed independently and compared; (5) causal edits (A→B) ordered correctly; (6) per-field independence, no phantom values. The harness asserts **convergence + bounded loss, *not* real-time correctness** — a fast clock winning a concurrent conflict is *expected*; silent divergence is the bug.
  - *Reorder (OQ-4):* (7) `keyBetween` fuzzed — `a < keyBetween(a,b) < b`, append/prepend bounds, and concurrent same-gap jitter stays strictly inside and distinct; (8) membership preserved (no lost/duplicated items — interleave is the only anomaly); (9) convergent strict-total order by `(position, itemId)`.
  - *Tombstones (OQ-2):* (10) no resurrection by a lower-HLC edit.
  - *Snapshot subsumption (OQ-2 door — and the actual v1 manual-plug path):* (11) `merge(deltas)` state == `merge(snapshot_of(deltas))` state, and `local ⊕ latest_snapshot` == `local ⊕ folded_deltas`.
  - *Plug contract (OQ-3), once the real adapter is wrapped:* (12) concurrent puts → distinct objects, no clobber; `list` returns only fully-published names.
  **Layering:** Layer 1 (pure merge/HLC, inv 1–11) lands *at step 4* as the gate on the LWW reducer, before manual sync; Layer 2 (wrap the real plug, inv 12) with step 5; Layer 3 (encrypted batches still converge — crypto is transparent to merge) with step 6.

### Crypto, identity & privacy — OQ-8–11 (resolved)

- **OQ-8 (shared-key threat model) is resolved — one symmetric root, gaps accepted and documented.**
  **Identity = a single 256-bit symmetric root secret** (no asymmetric keypair). v1 pairing rides a **single self-contained QR that carries the root** (+ checksum); recovery key is the camera-less fallback. This keeps v1 fully symmetric — no X25519/Ed25519 — so OQ-10's Android JCA API-gating is moot.
  **Threat model — protected:**
  - *Relay / backend operator (honest-but-curious):* sees ciphertext + metadata (OQ-9); cannot read plaintext or forge batches (AEAD). *Can* withhold / delete / serve-stale — an availability & rollback attack, accepted for v1.
  - *Network in transit:* transport TLS + E2E → ciphertext only.
  **Threat model — accepted gaps (mostly architecturally hard, not just deferred):**
  - *Lost / stolen device = full compromise* (holds the exportable root) → past + future data. **No revocation.**
  - *No forward secrecy / no post-compromise security:* one root compromise reads all past ciphertext ever logged on the relay, and all future.
  - *QR capture during pairing* = root capture (physical-trust model).
  - *Malicious sibling app (federation):* the user-consent handoff dialog is the only gate; otherwise the Android sandbox isolates. You trust apps you hand off to.
  **Why FS/revocation are ~incompatible:** forward secrecy needs key ratcheting, but a long-offline device must still decrypt old batches to merge `local ⊕ snapshot`, so it can't ratchet those keys away; revocation needs membership + a coordinating re-key authority, but the store is deliberately dumb and membership-less. CRDT-door-level "different model only" items.
  **Only response to device loss — manual "nuclear" rotation:** new root → re-pair trusted devices → write under the new root → abandon/delete old ciphertext. Can't un-read already-synced data; does stop new reads. Under **federation this fans out to every sibling app × every device** (O(devices × apps) re-pairs) — documented, and single-app v1 doesn't build it.
  **Door-opener (built in v1):** the OQ-10 batch header carries a **`keyGen` id**; clients hold a *set* of roots keyed by `keyGen`, so rotation is implementable later with no format change.

- **OQ-9 (relay metadata leakage) is resolved — enumerated and accepted; naming unchanged.**
  Even with E2E, the relay can infer:
  - **Inherent to any backend** (unmitigable without cover traffic): object **size** (≈ plaintext + AEAD overhead → how much changed), write/read **timing & frequency** (activity, timezone, waking hours), total **volume / count** (data scale), **source IP / account creds** (coarse geolocation — transport/account level), and **cross-app / cross-device linkage** if stores share a backend or credentials.
  - **Derived from the OQ-3 name:** `type` (snapshot cadence), `deviceId` (device count + per-device profiling), and `hlc range` — **approximate wall-clock time of the operations themselves**, which exposes *offline* edit times, not just sync times.
  - **Cannot infer:** any plaintext, field names/values, record contents, or the data-model version (encrypted header, OQ-6).
  - **`hash` leaks nothing — not even dedup:** OQ-10's random per-batch salt makes identical content encrypt to distinct ciphertext → distinct hash across devices.
  - **Decision:** keep the full OQ-3 name (`v{fmt}_{type}_{deviceId}_{hlcLo}_{hlcHi}_{hash}`) — the device-count and operation-timestamp leaks are **accepted** in exchange for cheap decrypt-free enumeration / pruning / snapshot-selection, consistent with the honest-but-curious relay model (OQ-8).
  - **Deferred mitigations (each addable with no format change):** size-bucketing (pad plaintext *inside* the ciphertext) to blunt the size leak; per-app credentials/buckets to reduce cross-app linkage; rotating/keyed pseudonymous `deviceId` to blunt per-device profiling. User-side: Tor/VPN for the IP leak, or self-host the relay so the "operator" is the user.

- **OQ-10 (crypto library + scheme) is resolved — cryptography-kotlin, symmetric AEAD data path, Tink rejected.**
  **Library:** **cryptography-kotlin** (whyoleg) behind the `core/crypto` interface — a KMP abstraction over vetted platform providers (JDK/Conscrypt on Android), clean misuse-resistant API, keeps the KMP door open. Dependency risk (younger, single-maintainer) is bounded: Apache-2 (vendorable) and swappable behind `core/crypto`. **Tink is rejected** — FOSS/F-Droid-legal but *is* Google, against the no-Google spirit, and JVM-only; equally-vetted non-Google options exist, so the exception isn't worth spending. **libsodium** (native `.so`) is the fallback if a primitive gap or misuse-resistance need appears.
  **Scheme (library-agnostic by design):**
  - **Root:** a 256-bit symmetric **group secret** = identity, shared via QR/recovery. The data path is **symmetric-only** — a shared-key AEAD gives confidentiality + integrity + intra-group authenticity, so no asymmetric crypto is required to encrypt batches. (Identity is the symmetric root; pairing is symmetric too — see OQ-8.)
  - **Per-batch:** random 256-bit `salt` → `K = HKDF-SHA256(root, salt, "msuite-batch-v1")` → `AEAD_Encrypt(K, fixed nonce, plaintext, aad = header)`. A fresh per-batch key makes **nonce reuse impossible regardless of AEAD nonce width**, decoupling correctness from the library's cipher choice. The header (`salt` + OQ-6 data-model version + OQ-8 `keyGen` id) is authenticated as AAD.
  - **AEAD:** AES-256-GCM (hardware-accelerated on modern Android); ChaCha20-Poly1305 acceptable alt.
  - **KDF:** HKDF-SHA-256. **Content address (OQ-3):** SHA-256 over the ciphertext object.
  - **Recovery:** encode the 256-bit root as BIP39-style 24 words / base32 (UX friction = OQ-11).
  - **Don't roll your own:** all primitives come from the library; our code only composes HKDF → AEAD.
  **Caveat carried to OQ-8:** cryptography-kotlin's JDK provider inherits Android JCA's API-level gating for X25519/Ed25519 (API 31/33+). The symmetric-only data path avoids this; *if* OQ-8's pairing needs asymmetric on older Android, add a BouncyCastle-backed provider (bundled, API-independent impls) or keep pairing to a symmetric secret transfer.

- **OQ-11 (recovery UX + at-rest) is resolved — key-recovery reframed, contextual nudge, OS/KeyStore at rest.**
  **Reframe:** the recovery key is *key* recovery, not *data* recovery. With 2+ devices or a backend, losing a device is a non-event; single-device-no-backend loss is a *durability* gap that no key scheme fixes. So the primary durability push is **pair a 2nd device (QR) or configure a backend**; the recovery key is the all-devices-lost tail case.
  **Recovery UX:**
  - **No key ceremony at setup.** A **contextual nudge** fires when durability starts to matter — on backend configuration, and/or when still single-device after a few days — with honest copy ("no account = no password reset").
  - **Mechanism:** offer the root as **words (BIP39) + QR/file + copy-to-clipboard**, framing "save to your password manager / cloud" as primary — not "transcribe 24 words."
  - **Deferred:** passphrase-based recovery (a passphrase-wrapped root on the backend, Argon2id) — a bigger familiar-UX win, but it adds a password, dilutes "no account," and needs a backend; parked behind the recovery interface.
  **At-rest on device:**
  - **DB + op-log:** rely on the **OS sandbox + file-based encryption** (protected on locked, non-rooted devices). No native code.
  - **Root secret:** stored as a **hardware-backed KeyStore-wrapped blob** (Jetpack Security / `EncryptedSharedPreferences`). The root stays *exportable* (OQ-8) — the KeyStore key only wraps the resting blob; export remains a user-authorized path. A device-local at-rest key *can* be non-exportable KeyStore precisely because it is never shared, unlike the root.
  - **Deferred:** **SQLCipher** DB encryption (resists rooted/extraction attacks) — opt-in later behind the storage layer; skipped in v1 to avoid a native `.so`, consistent with OQ-10's no-native lean.

### Scope & product — OQ-12–15 (resolved)

These four are strategic, not mechanical. **Framing (locks the others):** this is a **proof-of-concept hobby project with no adoption goal** — success is a working demonstration of the architecture + UX (see *Non-goals* and *the thesis*, above). So "adoption risk / acquisition wedge / adoption gate" language from earlier drafts is retired: OQ-13's wedge question dissolves, OQ-14 defers reminders simply because they prove none of the thesis, and OQ-15 keeps infra-first because the shared layer *is* the deliverable. OQ-12 additionally enforces **web-capable library choices** even though the `js` target isn't built.

- **OQ-12 (KMP `js` while web is deferred) is resolved — don't build the `js`/`wasm` target in v1, but keep every library choice web-capable.**
  Two separate things: *building* a web target now (pure tax — SQLDelight JS/wasm + crypto-in-JS friction against an unshipped target) vs. *choosing libs that can reach web later*. Drop the former; enforce the latter (per the steer: prefer web-capable libs wherever possible, so re-adding `js` is config, not a rewrite). Going *fully* pure-Android would also over-correct — the OQ-7 kotest harness wants to run as fast JVM unit tests, and a clean `commonMain` + web-capable libs is the cheap door-opener.
  - **v1 KMP targets = `android` + `jvm`.** `jvm` earns its place now (the harness runs there); `js`/`wasm` build isn't worth it until web is real. `core/storage` is annotated `(android, jvm)` accordingly.
  - **Core libs are already web-ready — nothing currently listed needs replacing:**

    | Core lib | Web path | Friction to watch |
    |---|---|---|
    | SQLDelight (`storage`) | sql.js (SQLite→wasm) worker driver, persisted to IndexedDB/OPFS | **the main watch-item** — the wasmJs driver is the least mature piece |
    | cryptography-kotlin (`crypto`) | **WebCrypto provider** — AES-256-GCM, HKDF-SHA-256, SHA-256 all in browser SubtleCrypto | none; symmetric-only path also sidesteps the X25519/Ed25519 API-gating caveat (OQ-10) |
    | Compose MP / Material 3 (`design`) | Compose for Web (wasmJs, Canvas) | rendering backend differs; theme tokens/components port |
    | Ktor client (HTTP plugs — WebDAV/S3/relay) | JS/fetch engine | **CORS** on S3/WebDAV/relay from a browser origin |
    | kotest-property (test) | test-only, KMP-capable | irrelevant to shipping |

  - **The web-*incompatible* bits are all inherently platform-specific and already live behind interfaces (app/platform layer, never `core`)** — so web is writing platform impls, not swapping core libs:

    | Android bit | Why not web | Web impl |
    |---|---|---|
    | SAF ("synced folder" plug) | no SAF in browser | File System Access API (`showDirectoryPicker`, Chromium) for folders; file up/download for the manual export/import plug |
    | KeyStore / EncryptedSharedPreferences (root at rest, OQ-11) | no hardware KeyStore in browser | WebCrypto **non-extractable** wrapping key in IndexedDB wraps the resting root blob — sandbox-backed, *weaker than hardware*; a documented web at-rest gap |
    | ZXing / zxing-cpp (QR scan, OQ-8 pairing) | native/camera | `BarcodeDetector` API or a JS QR lib + `getUserMedia` |
    | AlarmManager / WorkManager (reminders — deferred, OQ-14) | no reliable background alarms when tab closed | Notifications API + Service Worker; genuinely limited on web |

  - **Conclusion:** dropping the `js` build from v1 costs nothing architecturally — the interface discipline (IdentityProvider, backend-plug, and by extension QR-scan / at-rest / filesystem seams) already isolates every non-portable dependency. Keep choosing web-capable libs; add the `js` target when web is real.

- **OQ-13 (is "suite coherence" a user-visible wedge?) is resolved — the question dissolves: no adoption goal, so coherence isn't a go-to-market wedge, it's the *thesis being proven*.**
  Bring-your-own-backend E2E sync is well-trodden (Joplin, Standard Notes, KeePass, Obsidian+Syncthing); "one shared sync layer across a suite" is the novel, *developer*-facing part. Since this is a PoC, not a product play:
  - The value is the **demonstration itself** — that a solo dev can build a coherent, privacy-first, good-UX FOSS suite with serverless E2E sync. Judged on **engineering + UX quality**, not installs.
  - Coherence still has real *engineering* payoff — federation ("pair once, all msuite apps sync"; see *Identity, pairing & recovery*) + a shared design language make the suite cohere, which is exactly what the PoC shows off. But there's no "wedge"/"multiplier" claim to defend because there's no market to win.
  - **Upshot:** the point of the exercise is the shared layer + UX quality, so building it *is* the deliverable — which is why infra-first (OQ-15) is coherent with the goal rather than in tension with it.

- **OQ-14 (reminders/notifications) is resolved — deferred from v1 because they prove none of the tech thesis, not for any adoption-timing reason.**
  Reminders are the core ToDo job-to-be-done and are genuinely hard on FOSS Android (exact-alarm permission Android 12+/14, OEM battery-killers, no FCM). But:
  - **Out of v1** simply because they exercise none of the shared-layer thesis (sync / E2E / pairing) — pure app-feature surface. No adoption signal is sought or claimed (there is no adoption goal — OQ-13).
  - **A natural later feature** if the suite is ever used for real — not a scheduled "gate."
  - **Strategy (documented, not built):** `AlarmManager` (`USE_EXACT_ALARM`/`SCHEDULE_EXACT_ALARM`, Android 12+/14) for time-critical fires, `WorkManager` for inexact, a per-device "allow exact alarms" nudge, documented OEM battery-killer caveats (Xiaomi/Samsung/Huawei). No FCM (F-Droid rule). On web: Notifications API + Service Worker (limited — OQ-12).

- **OQ-15 (solo-maintainer scope realism) is resolved — keep infra-first; the concern is *finishing the PoC*, not product signal.**
  Infra + relay + F-Droid + crypto, unfunded and solo, is a lot — but since the shared layer *is* the deliverable (OQ-13), building it first is building the point, not deferring a product.
  - **Keep the current build order (`msuite-v1.md` steps 1–9 unchanged).** The OQ-7-gated de-risking sequence retires the hard engineering unknowns — *does serverless E2E suite sync actually work?* — in dependency order, each step proving one thing.
  - **Scope contained by discipline:** that sequence + **strict v1 minimalism** — a thin ToDo with everything non-essential deferred (reminders, subtasks, recurrence, attachments, web, CRDT, snapshots, revocation). v1 builds *only* the door-openers, never the deferred machinery.
  - **No product-signal framing** — there is no adoption risk to retire because adoption isn't a goal; the only risk that matters is whether the architecture + UX come together into a working demonstration.
