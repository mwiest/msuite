# Architecture

How the code is organised, and the sync design it is being built towards. What the apps do for the
user is in `product.md`; why it is built this way is in `decisions/`. A change in structure updates
this file in the same commit.

**Status (2026-10-14):** the merge core (v1 step 2) is code in `core/model` and `core/sync`, and
storage (step 3) in `core/storage`; the other modules are placeholders. The sections from "Sync" on describe the agreed design; parts that
are code say so. Each part becomes "what the code does" as its step in
[`plans/v1-todo.md`](plans/v1-todo.md) lands; mark it then.

## Modules

| Module | Kind | Holds (intended) | Today |
|---|---|---|---|
| `core/model` | KMP (android, jvm) | Domain types, stable record IDs (UUID) and stable opaque field/op IDs, op definitions, the data-model version header (= 1), the identity `upcast` hook, the batch/snapshot encoder interface (kotlinx.serialization ProtoBuf, decision 0016) | `Hlc`, `DeviceId`, `FieldId`, `CoreFields`, `Stamp`, `Cell`, `PlugId`, `FractionalIndex`, `Batch`, `ProtoBatchEncoder`, `Value`, `ValueCodec`, `DATA_MODEL_VERSION` |
| `core/storage` | KMP (android, jvm) | SQLDelight database (decision 0046): the generic `field_state` table (decision 0035), ingested batch keys, per-plug watermarks, the device ID | `LocalStore` over all four tables (see Sync → Data and merge) |
| `core/sync` | KMP (android, jvm) | HLC, LWW merge behind `merge()`, batch building and ingest, backend-plug interface (`put`/`list`/`get`), object keys, plug adapters | `HlcClock`, `merge()`, `ingest()`, `buildDelta()`, `Replica`, `BackendPlug`, `ManualSync`, simulator Layer 1 |
| `core/sync-testing` | Kotlin/JVM library, used by tests only | The multi-device simulator harness (decision 0057) | `Cluster`, `SimDevice`, `NetStep`, `SimState` (`MemoryState`, `ReplicaState`), `SimStore` (`BatchStore`, `PlugStore`), `MemoryPlug`, `assertPlugContract`, `TestClock` |
| `core/crypto` | KMP (android, jvm) | Root secret, per-batch HKDF into AES-256-GCM, QR pairing payload, BIP39 recovery (cryptography-kotlin, decision 0010) | Placeholder |
| `core/design` | Compose Multiplatform (android only for now, source in `commonMain`) | Material 3 theme and shared components (decision 0018) | `MsuiteTheme` wrapping `MaterialTheme` |
| `apps/todo/domain` | KMP (android, jvm), no Compose, package `com.msuite.todo.domain` | ToDo logic: IDs, record types and fields, the projection over `field_state`, read-time rules, commands (decision 0051) | `TodoIds`, `TodoTypes`, `TodoFields`, `projectTodos()`, `todoStates()`, `toPickerMillis()`, `TodoSettings`, `ListCommands`, `TaskCommands`, `SyncActions`, `SnapshotFiles` |
| `apps/todo/shared` | Compose Multiplatform (android only for now, source in `commonMain`), package `com.msuite.todo` | The ToDo UI: screens and composition root (decisions 0019, 0037, 0051) | `TodoApp(replica)` creates "My Tasks" if missing; `TodoContainer` wires the commands and `todoStates()`; `TasksScreen`, `TaskEditorSheet`, `ListMenu` and the list dialogs, `SettingsScreen` (see ToDo app wiring and UI) |
| `apps/todo/android` | Android application `com.msuite.todo` | Thin shell: `MainActivity`, manifest, resources, Android-only implementations (decision 0037) | `TodoApplication` opens the one `Replica` and the `TodoSettings`; `MainActivity` calls `TodoApp(replica, settings)`; backup off (decision 0060) |
| `server` | Kotlin/JVM application | The dumb relay, a Ktor CIO binary (decision 0017) | `main` printing a line |
| `web/` | Not a module | Deferred web app; see `web/README.md` | - |

Rules: apps depend on `core/*`, never on each other (decision 0021). Platform-only code (SAF,
KeyStore, camera QR, alarms) stays behind interfaces in the app layer, never in `core` (decision
0012). Libraries pinned in the catalog but not wired yet carry a `// TODO` at their use site.

## Build

- One version catalog: `gradle/libs.versions.toml`. It has no `[plugins]` section: Gradle plugins
  are dependencies of `build-logic` and are applied only through the convention plugins. Aliasing a
  plugin in a module would clash with the build-logic classpath.
- `build-logic/` is an included build (`pluginManagement { includeBuild("build-logic") }`) that
  reads the same catalog. Shared levels are in `build-logic/src/main/kotlin/MsuiteBuild.kt`
  (decision 0022: `minSdk` 26, `compileSdk`/`targetSdk` 37, JDK 21 toolchain, Java 17 bytecode).
  Module build files hold only plugins, namespace and their own dependencies.
- Toolchain (2026-10-11): Gradle 9.8, AGP 9.4, Kotlin 2.4, Compose Multiplatform 1.12. The exact
  versions are in the catalog and the wrapper.
- KMP modules use AGP's `com.android.kotlin.multiplatform.library`: the namespace is set in
  `kotlin { android { namespace = "..." } }`, and there is no top-level `android {}` block. The
  application module uses AGP's built-in Kotlin, so `org.jetbrains.kotlin.android` is not applied.
- Compose dependencies come from the catalog (`libs.compose.*`), not the deprecated `compose.*`
  accessors.
- UI text (decision 0047): `src/commonMain/composeResources/values/strings.xml` and `values-de/`,
  read through the generated `Res` (`com.msuite.<app>.resources`). A KMP library only packages
  them into the APK with `androidResources.enable = true` in its `android {}` target.

| Convention plugin | Sets up | Used by |
|---|---|---|
| `msuite.kmp-library` | KMP + AGP KMP library, targets `android` and `jvm`, JUnit Platform | `core/{model,storage,sync,crypto}`, `apps/todo/domain` |
| `msuite.compose-library` | KMP + AGP KMP library + Compose Multiplatform, android target only | `core/design`, `apps/todo/shared` |
| `msuite.android-app` | Android application with built-in Kotlin, Compose, `targetSdk` | `apps/todo/android` |
| `msuite.jvm-library` | Kotlin/JVM, JDK 21 toolchain, Java 17 bytecode, JUnit Platform | `core/sync-testing` |
| `msuite.jvm-app` | `msuite.jvm-library` plus `application` | `server` |
| `msuite.serialization` | the kotlinx.serialization compiler plugin | `core/model` |
| `msuite.sqldelight` | the SQLDelight plugin; the module declares its database | `core/storage` |
| `msuite.quality` | Spotless (ktlint, rules in `.editorconfig`) and detekt (`config/detekt/detekt.yml`), applied by all of the above and the root project | everything |

- `gradle/gradle-daemon-jvm.properties` pins the Gradle daemon to JDK 21, so a newer `JAVA_HOME`
  (e.g. Android Studio's JDK 25) works as long as a JDK 21 is installed where Gradle finds it. No
  toolchain download is configured. Gradle itself runs on JDK 25; the pin is needed because
  detekt 1.23 doesn't.
- `.gitattributes` forces LF (CRLF only for `*.bat`); Spotless fails on CRLF files.
- CI (decision 0025): `.github/workflows/ci.yml` runs lint, tests, then the debug APK and relay
  jar. Renovate (`renovate.json`) groups Kotlin and Compose Multiplatform updates.

## Sync

The design in short (decision 0028): backends store only E2E-encrypted batches; all merge logic is
in the client.

### Data and merge

- All synced state is one generic table in `core/storage`, the same for every app (decision 0035):

  ```
  field_state(record_id, field_id, value BLOB, hlc, device_id)   primary key (record_id, field_id)
  ```

  `value` is encoded with the batch encoder. The record type is an ordinary field with a reserved
  ID. Apps read typed models through their own projection code.
- Field IDs 1 to 9 are reserved for every app (code: `CoreFields` in `core/model`, decision 0054):
  `TYPE = 1` holds the app's `Int64` record type ID, `DELETED = 2` the tombstone. App fields start
  at 10.
- Storage (code: `LocalStore` in `core/storage`, schema in `src/commonMain/sqldelight`): SQLite
  tables `field_state`, `ingested_batch(plug, batch_key)` and `plug_watermark(plug, hlc)`, keyed
  by `PlugId` (a stable name per configured plug); the last two sit behind `LocalStore.plugs`. A
  one-row table `device` holds this device's ID (decision 0058). It
  knows no merge or HLC rules. HLCs are stored as the packed value with its sign bit flipped, so
  SQL compares them unsigned like `Hlc`. The platform creates the `SqlDriver` with
  `LocalStore.Schema`; JVM tests use sqlite-jdbc (decision 0046).
- `Replica` in `core/sync` (code) is the device's state: `Replica.open` takes the `SqlDriver`,
  owns the `LocalStore` on it, stamps with the stored device ID (created on first open), and holds the `HlcClock`, which starts from the store's highest HLC, so
  writes after a restart order after everything stored even if the wall clock went back. A local
  write stamps all its cells with one fresh HLC. Ingest skips a batch key already ingested from
  that plug, and writes the winners and marks the key in one transaction. A delta is built from
  the plug's watermark, which moves on `pushed`. Every operation holds a coroutine `Mutex` and
  runs in one transaction, in the replica's coroutine context (default `Dispatchers.Default`, so
  the blocking driver stays off the UI thread, decision 0059). The app opens one replica per database and writes only through it;
  the mutex doesn't cover a second one. `changes`, a `StateFlow`, ticks after each committed write
  or ingest that changed state; the UI re-reads on a tick (decision 0049).
- Deletes are an ordinary LWW `deleted` field (tombstone). Clearing a field writes its cell with
  an empty value, which decodes as absent (decision 0056). Malformed value bytes also decode as
  absent, so a bad cell can't crash a reader.
- `merge(local, remote)` in `core/sync` (code) is per-cell last-writer-wins on the total order
  `(pt, c, deviceId)` of the Hybrid Logical Clock (decision 0005), held in a `Stamp`. It is a pure
  function of state, so merge is convergent and order-independent, and it knows nothing about the
  app. It returns the remote cells that win, which is what storage writes. If a device ever reuses
  a stamp for two values, the higher value (bytes compared unsigned) wins, so devices still agree.
- HLC (code: `Hlc` in `core/model`, `HlcClock` in `core/sync`): 48-bit millisecond `pt`, 16-bit
  counter `c`, packed into 64 bits and ordered unsigned; a full counter carries into `pt`. Ingest
  defers batches whose `hlcHi.pt` is more than 24 h ahead of the local clock (`HlcClock.accepts`),
  so the local HLC never runs more than 24 h ahead of the wall clock (decision 0040). The wall
  clock is an injected `kotlin.time.Clock`.
- `deviceId` is a random 64-bit value, ordered unsigned; field IDs are `Int`s (decision 0043).
- No local op-log (decision 0036). An outgoing delta is every cell with an HLC newer than the
  plug's local watermark. The device also keeps the set of batch names it has ingested. Code:
  `buildDelta` and `ingest` in `core/sync`; `ingest` defers a batch beyond the 24 h bound
  (`IngestResult.Deferred`), holds back one from a newer data-model version (`IngestResult.TooNew`,
  decision 0067), or merges it and advances the HLC. A fetched batch that won't decode is skipped
  and retried a bounded number of times, then kept and flagged corrupt (`decodeOrNull`,
  `Replica.unreadable`, decisions 0068, 0069).
- Unknown field IDs from newer app versions are rows the app doesn't read; they are merged and
  written out like any other (decisions 0006, 0035).
- Manual order is a `position` field holding a fractional-index string with a random suffix; lists
  sort by `(position, itemId)` (decision 0004). Code: `FractionalIndex.keyBetween` in `core/model`,
  base62 keys with a 2-character suffix (decision 0044). Keys compare bytewise.
- Records that exist once per app (the default list) have constant IDs in code (decision 0031).

### Read-time rules (ToDo)

Code: `projectTodos()` in `apps/todo/domain`, which reads cells into sorted lists and tasks. Edits
are `ListCommands` and `TaskCommands` there, each one `Replica.write`: a new list goes to the end,
a new task to the top of its list, and a move places the item at an index of the shown order. An
edit checks that its ID is a record of the right type (hidden ones included) and throws otherwise.

- A task whose list is deleted is hidden everywhere; deleting a list writes only the list's
  `deleted` field (decision 0032). This holds for "My Tasks" too (decision 0055).
- A record without a known `TYPE` is not shown (its creation may not have synced yet), nor is a
  task without a live list. A value of an unexpected type reads as absent.
- Lists and tasks sort by `(position, id)`; positions compare by code unit, which for base62 keys
  is bytewise (decision 0044). Within a list, open tasks come before done ones, and a task moves
  only among tasks with its done state (decision 0065).

### ToDo app wiring and UI

The shell's `TodoApplication` is the composition root: one `Replica` per process on `todo.db`
(decisions 0050, 0058) and `TodoSettings` on `SharedPreferences` (decision 0064); `MainActivity`
passes both to `TodoApp` and sets the system bar style from the theme. In `shared`, `TodoContainer`
holds the commands and `todoStates()`; screens launch commands in their own scope, and the replica
moves the work off the UI thread (decision 0059). Navigation Compose has two routes: the tasks
screen (lists as tabs, decision 0061; the task editor sheet, decision 0062; list menu, dialogs and
drag to reorder, decision 0063) and settings (decision 0064), which ends with an About section
(version from the shell). The open list is remembered in `TodoSettings` (decision 0065). Icons are
Material vector drawables in the resources. Testable logic sits in `domain` (`TodoState.selected`,
`TodoList.deletable`, `toPickerMillis`, `TodoSettings`); `shared` has no test target (decision
0051), so screens are checked on an emulator.

### Batch encoding

kotlinx.serialization ProtoBuf behind the `BatchEncoder` interface in `core/model` (code:
`ProtoBatchEncoder`, decision 0016). Proto field numbers are the stable IDs. A batch is the
data-model version plus its cells sorted by `(recordId, fieldId)`, so encoding is deterministic.
A cell's value is a typed one-field ProtoBuf message (`Value`, `ValueCodec`); a type the reader
doesn't know decodes as absent. Value types: `Text`, `Bool`, `Int64`, `Date` (a `LocalDate` as
epoch days, decision 0052) and `Ref` (a record's `Uuid`, decision 0053). Layout and field numbers: decision 0045.

### Batch envelope (encryption)

Planned for v1 step 6 (decisions 0010, 0029):

```
header:     salt (256 bit random), data-model version, keyGen id
key:        K   = HKDF-SHA256(root, salt, "msuite-batch-v1")
aad:        header + object key without the hash ({appId}/{groupId}/v{fmt}_{type}_{deviceId}_{hlcLo}_{hlcHi})
ciphertext: AES-256-GCM(K, fixed nonce, encoded batch, aad)
```

A reader rebuilds the AAD from the key it fetched, so a renamed or moved batch fails to decrypt.
The ciphertext of a batch is computed once and cached, so retries resend identical bytes.

### Backend plugs

One append-only interface (decision 0003): `put(key, bytes)` (create-only, atomic),
`list(prefix)` (keys only), `get(key)`. `delete` and a capability descriptor arrive with
snapshots and the relay.

Object keys (decision 0029):

```
{appId}/{groupId}/v{fmt}_{type}_{deviceId}_{hlcLo}_{hlcHi}_{hash}
```

- `appId`: the app's short lowercase name (`todo`). `groupId`: first 128 bits of
  `HKDF-SHA256(root, "msuite-group-v1")`, the same for all apps of a group. Together they are the
  namespace.
- `v{fmt}`: naming-scheme version. `type`: `d` (delta) or `s` (snapshot).
- `deviceId`: the single writer.
- `hlcLo`..`hlcHi`: the HLC span covered, 64 bits each.
- `hash`: SHA-256 over the ciphertext.
- Binary fields are unpadded uppercase base32hex (`0-9A-V`), fixed width, so they sort in value
  order. The name charset is `[A-Z0-9_]`.

Syncing is list-and-diff: `list({appId}/{groupId}/)` minus the keys already ingested, then `get`
the rest.

| Plug | One batch is | Namespace is | Atomic publish | Create-only | `list` |
|---|---|---|---|---|---|
| Manual (v1) | the whole full-state snapshot, one file | - | file write | each export is a fresh compacted snapshot | - |
| Synced folder (SAF) | a file | nested subfolders | write `.tmp_*`, then rename | unique names; a retry resends identical bytes | list children |
| WebDAV | a resource | nested collections | `PUT`, or `PUT` temp + `MOVE` | `If-None-Match: *` | `PROPFIND Depth:1` |
| Object storage (S3, R2, B2, MinIO) | an object | a key prefix | native | `If-None-Match: *` | `ListObjectsV2(prefix)` |
| Relay | a file on disk | nested directories | relay does temp + rename | relay rejects existing names and checks the hash | relay lists the namespace |

The manual plug is a single-file export/import holding one full-state snapshot (code: `ManualSync`
in `core/sync`; decision 0066). Importing merges `local ⊕ snapshot`; a snapshot is a delta over
every field, so this exercises the same reducer. Until encryption lands (step 6) the export is
plaintext: the manual path is a proof of concept until then, not a way to carry real data. The live
plugs implement `BackendPlug`; the
multi-object keys are first used at runtime by the synced-folder plug, the v1 milestone (decision
0042). Its logic sits behind a small file-system interface, so the simulator runs it on `jvm` with
plain files.

### Compaction (planned)

The append-only contract grows three things without bound: backend objects, `list` enumeration and
the client's `ingested_batch` bookkeeping. All three are bounded by 0002's rule — a writer's
full-state snapshot subsumes its every earlier object — in three parts, landing with the folder
plug (v1 step 8) and `delete` (v1 step 9):

- **Span skip.** A reader that has ingested a writer's snapshot skips that writer's objects whose
  `hlcLo`..`hlcHi` span lies inside it: the key grammar (0029) carries the span, so no `delete` is
  needed and *fetching* stays bounded on every backend (enumeration is bounded by the delete
  below). `ingested_batch` and `unreadable_batch` are caches, not correctness (merge is
  state-based), so rows whose keys are no longer in `list` are pruned with it.
- **Writer-side pruning.** After publishing a snapshot, a device deletes its own earlier objects,
  optionally keeping a short grace window (a key absent at `get` is a skip, not corruption —
  0069). `delete` is capability-gated (0003). The namespace authenticates the group's shared root
  (0008), so "own objects" is a convention every member could break; the recovery rule covers that
  and a backend that lost data alike.
- **Recovery.** A device that finds its published objects gone publishes a fresh snapshot. A delta
  would not re-expose them — its watermark has moved — but a snapshot re-exposes its whole state.

Steady state publishes deltas, coarsely batched (per idle period, not per edit); snapshots are for
joins (0033) and the periodic compaction of 0002.

### Relay

`server/`: one Ktor CIO binary, filesystem blob storage, manual wiring, no reflection (decision
0017). It implements the plug contract over HTTP and reuses the key and contract types from
`core`. Access per namespace (decision 0030): the client sends
`HKDF-SHA256(root, "msuite-relay-auth-v1:" + appId)` as a bearer token; the relay stores only its
SHA-256, registered by the first `put` to the namespace. The operator can restrict registration and
cap namespace size. Cloudflare users need no relay: R2 works with the object-storage plug.

## Identity, pairing and recovery

- Identity is one 256-bit symmetric root secret (decision 0008), sourced behind
  `IdentityProvider` / `SyncConfig`: local key generation in v1, a sibling app later (federation,
  decision 0001).
- Pairing: one QR code carrying the root plus a checksum. Recovery: the root as BIP39 words, QR or
  file (decision 0011).
- A joining device keeps its data: it switches to the group's root, keeps its `deviceId`, and
  publishes its full state into the group's namespace (decision 0033).
- Copying the recovery key marks the clip sensitive and clears it after 60 s (decision 0041).
- Clients hold a set of roots keyed by `keyGen`, so rotation can come later without a format change.
- At rest: database under the OS sandbox and file-based encryption. The root is wrapped with a
  non-exportable AES-256-GCM key in the Android KeyStore and stored in a private file (decision
  0039).
