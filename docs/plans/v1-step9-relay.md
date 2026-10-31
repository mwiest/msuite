# Plan: v1 step 9, the relay and the relay plug

Status: **slices 1 to 6 done (2026-10-31); slices 7 to 12 open.** Started 2026-10-31. Parent: [`v1-todo.md`](v1-todo.md) step 9.

## Goal

Two paired devices sync live through a self-hosted msuite relay, with no sync tool in between: the
relay stores only ciphertext under the keys of decision 0029 and admits a namespace only with the
group's root-derived token (decision 0030). On the same step, live sync stops growing without
bound on every live plug: devices publish snapshots, prune their own older objects, skip what a
snapshot covers, and republish when their objects are gone (`architecture.md` -> Compaction). This
closes the roadmap's "no snapshots or pruning yet" and "an emptied folder is not refilled".

## Decisions

Answered on 2026-10-31; each gets its record with the slice that first needs it.

- **Module:** a new KMP module `core/relay` holds the wire contract (paths, headers, status codes,
  the token) and `RelayPlug` over the Ktor client. `server/` depends on its `jvm` target, so the
  contract can't drift (decision 0017). Keeps Ktor out of `core/sync` and the folder path.
- **HTTP engine:** OkHttp on Android, injected by the shell; `core/relay` sees only an
  `HttpClientEngine`, tests use Ktor's in-process test client.
- **TLS:** the relay speaks plain HTTP; a reverse proxy or the host (Caddy, fly.io, Koyeb)
  terminates TLS. The app accepts only `https://` relay URLs; debug builds also accept `http://` for
  LAN and emulator tests.
- **Compaction cadence:** a device publishes a snapshot once [`SNAPSHOT_EVERY`] (20) of its own
  deltas are listed since its last snapshot, and on recovery. It prunes only after the snapshot
  itself is listed (decision 0077), never on `put` alone.

Proposed here, confirm or overrule before the slice that needs them (see Open questions):

- **HTTP API** (all under `/v1`): `PUT /v1/{appId}/{groupId}/{name}` (body = object bytes),
  `GET /v1/{appId}/{groupId}/{name}`, `DELETE /v1/{appId}/{groupId}/{name}`, and
  `GET /v1/{appId}/{groupId}/` for the listing: `text/plain`, one name per line. Names are
  validated by the shared `ObjectKey` grammar (decision 0079), so the relay needs no serializer. `GET /health` for step 10.
- **Status codes:** `201` created, `200` already present (a create-only put of an existing key is
  success, decision 0003), `400` a name that doesn't parse or a body whose SHA-256 doesn't match its
  `hash`, `401` a wrong or missing token, `403` registration refused (allowlist), `404` absent,
  `413` object too large, `507` namespace full. A listing or `get` on an unregistered namespace is
  an empty `200` / `404`, so a group's first device can list before it puts.
- **Token:** `HKDF-SHA256(root, info = "msuite-relay-auth-v1:" + appId)`, 32 bytes, sent as
  `Authorization: Bearer <base32hex>`; derived on each start, never stored (decision 0030).
- **Relay storage:** `{dataDir}/{appId}/{groupId}/{name}`, plus `{appId}/{groupId}/.token` holding
  `SHA-256(token)`. A dotfile can't collide with a key. Create-only publish writes a temp file, then
  hard-links it to the final name (`Files.createLink` fails if the name exists; a POSIX rename
  would silently replace it), then deletes the temp.
- **Relay config:** environment variables, read once in `main` (programmatic, decision 0017):
  `MSUITE_RELAY_PORT`, `MSUITE_RELAY_DATA`, `MSUITE_RELAY_ALLOW_GROUPS` (comma-separated `groupId`s;
  unset = open), `MSUITE_RELAY_MAX_OBJECT_BYTES`, `MSUITE_RELAY_MAX_NAMESPACE_BYTES`. Env vars suit
  step 10's cloud hosts. Defaults: port 8080, `relay-data`, 16 MiB per object, 1 GiB per namespace.
- **One backend at a time:** Settings chooses none, folder or relay. Two at once needs per-plug
  change tracking (`roadmap.md` -> Ideas).
- **No capability descriptor yet:** both live plugs can `delete`, and the manual plug isn't a
  `BackendPlug` (decision 0066), so a descriptor would have no `false` case (decision 0034: no
  deferred machinery). It comes with the first plug that can't delete.

## Slices

Test-first; each slice builds and leaves `./gradlew spotlessCheck detekt allTests assembleDebug
:server:jar` green.

1. [x] **`core/relay` and the wire contract.** New module (`msuite.kmp-library`, depends on
   `core/sync`); catalog adds `ktor-client-core`, `ktor-client-okhttp`, `ktor-server-test-host`
   (decision record: the module, engine and TLS answers above). `RelayApi`: path building and
   parsing for keys and listings, status-code constants, `relayToken(root, appId)`. Tests: token
   is deterministic, differs per app, and differs from `groupId`; paths round-trip; a name with
   `/`, `..` or outside the key grammar is rejected. After it lands: `c364585`.
2. [x] **Relay storage.** `BlobStore` in `server` over a data directory: create-only `put` (temp +
   hard link), `list`, `get`, `delete`, the hash check against the key, the namespace registry
   (`.token`, linked into place so two first puts race safely), byte accounting per
   namespace (scanned at start, kept in memory). kotest in `server/src/test` on a temp dir: create
   wins once under concurrent puts, a half-written temp is never listed, a wrong hash is refused, a
   restart re-reads sizes. After it lands: `7100731`.
3. [x] **Relay HTTP.** `embeddedServer(CIO)` with the routes and status codes above, bearer auth
   against the registry, trust on first `put` (decision 0030), the allowlist and both size limits,
   env config, `/health`; `main` replaces the placeholder. Tests with `testApplication`: every
   status code, a second group can't read the first's names, an unregistered namespace lists
   empty. `./gradlew :server:run` serves. After it lands: `3203451`.
4. [x] **`RelayPlug`.** `BackendPlug` over a Ktor `HttpClient` built from an injected engine; maps
   `401`/`403`/`413`/`507` to `RelayRefused(reason)` and transport failures to `IOException`, so
   `LiveSync` marks the plug unhealthy as it does for the folder. Tests in `server/src/test`, where
   client and relay meet: `assertPlugContract` against the in-process relay, and the plug's error
   mapping. After it lands: `bb054d6`.
5. [x] **Simulator Layer 2 and 3 over the relay.** `PlugStore` and `EnvelopePlugStore` over
   `RelayPlug` against the in-process relay, in memory and through `Replica`; invariant 12 with
   concurrent puts of the same and of distinct keys. `testing.md` gets the row. After it lands:
   `171a688`.
6. [x] **Backend-neutral live sync in the domain.** `FolderSyncActions` becomes `LiveSyncActions`
   over the chosen backend (`SyncBackend.None` / `Folder(uri)` / `Relay(url)` in `TodoSettings`,
   migrating the stored folder URI); polling, flush and status unchanged. `PlugId` per backend
   (`folder:<uri>`, `relay:<url>`). Existing folder tests pass unchanged in behaviour; new tests:
   switching backend stops the old sync, a relay status carries `RelayRefused`. After it lands:
   `aae79c7`.
7. [ ] **Relay in the app.** `INTERNET` permission; the shell provides the OkHttp engine through
   `ShellServices`; a debug-only network security config allows cleartext. Settings: backend
   chooser, relay URL field (https only in release), the group ID to give an operator's allowlist,
   and status for unreachable, refused and full, in English and German. `product.md` updated.
   Checked on an emulator against `./gradlew :server:run` on the host (`http://10.0.2.2:<port>`).
   After it lands: `<commit>`.
8. [ ] **Pairing carries the relay.** `BackendType.Relay(2)` whose section is the relay URL in
   UTF-8 (record: the section layout; decision 0072 makes a new type id no format change). A
   joining device that scans it preselects the relay with that URL; the token comes from the root.
   Tests in `core/crypto` (round trip, a relay section that isn't a URL is refused, an old reader
   still pairs) and `PairingTest`. After it lands: `<commit>`.
9. [ ] **Compaction: span skip in core.** The simulator's `isCoveredBySnapshot` moves to
   `core/sync` as production code, used by both `Cluster` and `LiveSync`'s pull: a key inside an
   ingested snapshot of the same writer is not fetched. Tests: covered and uncovered spans,
   another writer's snapshot doesn't cover. After it lands: `<commit>`.
10. [ ] **Compaction: snapshot and prune in `LiveSync`.** After [`SNAPSHOT_EVERY`] own listed
    deltas, publish the whole state (`replica.cells()`, as `ManualSync.export` builds it) as type
    `s`; once a listing shows it, delete this device's
    objects its span covers. Applies to every live plug, so the folder gets it too. Tests on two
    `Replica`s over `MemoryPlug`: invariants 18 and 19 through the real `LiveSync`, no prune
    before the snapshot is listed, a reader mid-fetch skips a pruned key (0069). After it lands:
    `<commit>`.
11. [ ] **Compaction: recovery.** A pull that lists no object of this device while it has a
    watermark and no unconfirmed publish rewinds the watermark and publishes a snapshot. Tests:
    an emptied plug is refilled and a fresh peer converges from it. `roadmap.md` drops the
    "emptied folder not refilled" problem and the reset-by-hand note in `testing.md`. After it
    lands: `<commit>`.
12. [ ] **Device check.** Two Android phones on the relay: `server` jar on a LAN machine behind
    Caddy (TLS) or plain HTTP with a debug build. Pair by QR carrying the relay; simultaneous,
    staggered and offline edits; a 10-minute soak ending identical cell for cell; relay restart and
    relay down (status, then recovery); a namespace wiped on the relay (refilled); the object count
    stays bounded after many edits. Repeat the count check on the Dropbox folder. `testing.md`
    gets "Two-device check (relay)"; `architecture.md`'s Relay and Compaction sections become
    "what the code does"; the plan closes per `AGENTS.md`. After it lands: `<commit>`.

## Open questions

For Marc, before the slice named:

- **API, status codes, storage layout, env config** (slices 1 to 3): as proposed above?
- **No capability descriptor** (slice 4): 0003 and the plan say "capability-gated `delete`"; this
  plan defers the descriptor until a plug can't delete. Needs a record either way.
- **Grace window** (slice 10): prune everything the snapshot covers (a key gone at `get` is a skip,
  0069, so a slow reader loses nothing), or keep the last few deltas? Proposed: none.
- **`SNAPSHOT_EVERY` = 20** (slice 10): a snapshot is the device's whole state, echoed peer cells
  included, so it is the large object; 20 keeps at most about 21 objects per writer.
- **Direct put confirmation** (slice 4): the relay's listing is strongly consistent, so 0077's
  listing-based confirmation costs one poll; keep it uniform across plugs (proposed) or let the
  relay confirm on `201`.
- **Server classpath:** `core/relay` -> `core/sync` brings `core/storage` (the SQLDelight runtime,
  no driver) onto the relay. Harmless for a jar, but if the native image of 0017 comes, the key
  grammar may move to a smaller module.

## How to verify

- `./gradlew allTests` green, including the relay contract, Layers 2 and 3 over the relay, and
  invariants 18 and 19 through `LiveSync`.
- The device check of slice 12 passes and is recorded in `testing.md`.
- The relay's data directory holds only ciphertext under 0029 keys and one `.token` hash per
  namespace; each writer's object count stays near `SNAPSHOT_EVERY` + 1.
