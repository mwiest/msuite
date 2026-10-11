# Architecture

How the code is organised, and the sync design it is being built towards. What the apps do for the
user is in `product.md`; why it is built this way is in `decisions/`. A change in structure updates
this file in the same commit.

**Status (2026-10-11):** the module skeleton builds; every module is still a placeholder. The
sections from "Sync" on describe the agreed design, not code. Each part becomes "what the code
does" as its step in [`plans/v1-todo.md`](plans/v1-todo.md) lands; mark it then.

## Modules

| Module | Kind | Holds (intended) | Today |
|---|---|---|---|
| `core/model` | KMP (android, jvm) | Domain types, stable record IDs (UUID) and stable opaque field/op IDs, op definitions, the data-model version header (= 1), the identity `upcast` hook, the batch/snapshot encoder interface (kotlinx.serialization ProtoBuf, decision 0016) | `DATA_MODEL_VERSION` constant |
| `core/storage` | KMP (android, jvm) | SQLDelight database: the generic `field_state` table (decision 0035), ingested batch keys, per-plug watermarks | Placeholder |
| `core/sync` | KMP (android, jvm) | HLC, LWW merge behind `merge()`, batch building and ingest, backend-plug interface (`put`/`list`/`get`), object keys, plug adapters | Placeholder, kotest smoke test |
| `core/crypto` | KMP (android, jvm) | Root secret, per-batch HKDF into AES-256-GCM, QR pairing payload, BIP39 recovery (cryptography-kotlin, decision 0010) | Placeholder |
| `core/design` | Compose Multiplatform (android only for now, source in `commonMain`) | Material 3 theme and shared components (decision 0018) | `MsuiteTheme` wrapping `MaterialTheme` |
| `apps/todo/shared` | Compose Multiplatform (android only for now, source in `commonMain`), package `com.msuite.todo` | The ToDo app: screens, view logic, projection over `field_state`, composition root (decisions 0019, 0037) | `TodoApp()` showing a placeholder |
| `apps/todo/android` | Android application `com.msuite.todo` | Thin shell: `MainActivity`, manifest, resources, Android-only implementations (decision 0037) | `MainActivity` calling `TodoApp()` |
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

| Convention plugin | Sets up | Used by |
|---|---|---|
| `msuite.kmp-library` | KMP + AGP KMP library, targets `android` and `jvm`, JUnit Platform | `core/{model,storage,sync,crypto}` |
| `msuite.compose-library` | KMP + AGP KMP library + Compose Multiplatform, android target only | `core/design`, `apps/todo/shared` |
| `msuite.android-app` | Android application with built-in Kotlin, Compose, `targetSdk` | `apps/todo/android` |
| `msuite.jvm-app` | Kotlin/JVM `application`, JDK 21 toolchain, Java 17 bytecode | `server` |
| `msuite.serialization` | the kotlinx.serialization compiler plugin | `core/model` |
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
- Deletes are an ordinary LWW `deleted` field (tombstone).
- `merge(local, remote)` in `core/sync` is per-cell last-writer-wins on the total order
  `(pt, c, deviceId)` of the Hybrid Logical Clock (decision 0005). It is a pure function of
  state, so merge is convergent and order-independent, and it knows nothing about the app.
- HLC: 48-bit millisecond `pt`, 16-bit counter `c`. Ingest defers batches whose `hlcHi.pt` is more
  than 24 h ahead of the local clock, and the local HLC never runs more than 24 h ahead of the
  wall clock (decision 0040).
- No local op-log (decision 0036). An outgoing delta is every cell with an HLC newer than the
  plug's local watermark. The device also keeps the set of batch names it has ingested.
- Unknown field IDs from newer app versions are rows the app doesn't read; they are merged and
  written out like any other (decisions 0006, 0035).
- Manual order is a `position` field holding a fractional-index string with a random suffix; lists
  sort by `(position, itemId)` (decision 0004).
- Records that exist once per app (the default list) have constant IDs in code (decision 0031).

### Read-time rules (ToDo)

- A task whose list is deleted is hidden everywhere; deleting a list writes only the list's
  `deleted` field (decision 0032).

### Batch encoding

kotlinx.serialization ProtoBuf behind an encoder interface in `core/model` (decision 0016). Proto
field numbers are the stable IDs. Encoding is deterministic: canonical field and map order.

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

The manual plug is a single-file export/import holding one full-state snapshot. Importing merges
`local ⊕ snapshot`; a snapshot is a delta over every field, so this exercises the same reducer.
Until encryption lands (step 6) the export is plaintext and debug-only; after that it is
encrypted. The multi-object keys are first used at runtime by the synced-folder plug, the v1
milestone (decision 0042). Its logic sits behind a small file-system interface, so the simulator
runs it on `jvm` with plain files.

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
