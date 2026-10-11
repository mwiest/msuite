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
| `core/storage` | KMP (android, jvm) | SQLDelight database: op-log store, materialized per-field state `(value, HLC, deviceId)` including opaque unknown field IDs (decision 0006) | Placeholder |
| `core/sync` | KMP (android, jvm) | HLC, op-log append and merge, LWW reducer behind `merge()`, backend-plug interface (`put`/`list`/`get`), batch naming, plug adapters | Placeholder, kotest smoke test |
| `core/crypto` | KMP (android, jvm) | Root secret, per-batch HKDF into AES-256-GCM, QR pairing payload, BIP39 recovery (cryptography-kotlin, decision 0010) | Placeholder |
| `core/design` | Compose Multiplatform (android only for now, source in `commonMain`) | Material 3 theme and shared components (decision 0018) | `MsuiteTheme` wrapping `MaterialTheme` |
| `apps/todo` | Android application `com.msuite.todo` | The ToDo app and its composition root (decision 0019) | Empty `MainActivity` |
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
  (decision 0022: `minSdk` 26, `compileSdk`/`targetSdk` 36, JDK 21 toolchain, Java 17 bytecode).
  Module build files hold only plugins, namespace and their own dependencies.

| Convention plugin | Sets up | Used by |
|---|---|---|
| `msuite.kmp-library` | KMP + Android library, targets `androidTarget` and `jvm`, JUnit Platform | `core/{model,storage,sync,crypto}` |
| `msuite.compose-library` | KMP + Android library + Compose Multiplatform, android target only | `core/design` |
| `msuite.android-app` | Android application, Kotlin, Compose, `targetSdk` | `apps/todo` |
| `msuite.jvm-app` | Kotlin/JVM `application`, JDK 21 toolchain, Java 17 bytecode | `server` |
| `msuite.serialization` | the kotlinx.serialization compiler plugin | `core/model` |
| `msuite.quality` | Spotless (ktlint, rules in `.editorconfig`) and detekt (`config/detekt/detekt.yml`), applied by all of the above and the root project | everything |

- `gradle/gradle-daemon-jvm.properties` pins the Gradle daemon to JDK 21, so a newer `JAVA_HOME`
  (e.g. Android Studio's JDK 25) works as long as a JDK 21 is installed where Gradle finds it. No
  toolchain download is configured.
- `.gitattributes` forces LF (CRLF only for `*.bat`); Spotless fails on CRLF files.
- CI (decision 0025): `.github/workflows/ci.yml` runs lint, tests, then the debug APK and relay
  jar. Renovate (`renovate.json`) groups Kotlin and Compose Multiplatform updates.

## Sync

The design in short (decision 0028): backends store only E2E-encrypted batches; all merge logic is
in the client.

### Data and merge

- Every record field is stored as `(value, HLC, deviceId)`. Deletes are an ordinary LWW `deleted`
  field (tombstone).
- An edit is an op, `set field X = Y @ HLC on device D`. Ops are grouped into batches.
- `merge(local, remote)` is per-field last-writer-wins on the total order `(pt, c, deviceId)` of
  the Hybrid Logical Clock (decision 0005). It is a pure function of state, so merge is convergent
  and order-independent.
- The op-log is transport: a synced batch collapses into state (decision 0002). v1 keeps all
  batches anyway.
- Manual order is a `position` field holding a fractional-index string with a random suffix; lists
  sort by `(position, itemId)` (decision 0004).
- Unknown field IDs from newer app versions are kept opaquely and written back out (decision 0006).

### Batch encoding

kotlinx.serialization ProtoBuf behind an encoder interface in `core/model` (decision 0016). Proto
field numbers are the stable IDs. Encoding is deterministic: canonical field and map order.

### Batch envelope (encryption)

Planned for v1 step 6 (decision 0010):

```
header (authenticated as AAD): salt (256 bit random), data-model version, keyGen id
key:        K = HKDF-SHA256(root, salt, "msuite-batch-v1")
ciphertext: AES-256-GCM(K, fixed nonce, encoded batch, aad = header)
```

The ciphertext of a batch is computed once and cached, so retries resend identical bytes.

### Backend plugs

One append-only interface (decision 0003): `put(name, bytes)` (create-only, atomic),
`list(prefix)` (names only), `get(name)`. `delete` and a capability descriptor arrive with
snapshots and the relay.

Batch names, charset `[A-Z0-9_]`:

```
v{fmt}_{type}_{deviceId}_{hlcLo}_{hlcHi}_{hash}
```

- `v{fmt}`: naming-scheme version. `type`: `d` (delta) or `s` (snapshot).
- `deviceId`: the single writer, unpadded uppercase base32.
- `hlcLo`..`hlcHi`: the HLC span covered.
- `hash`: SHA-256 over the ciphertext, unpadded uppercase base32 (52 chars).

Syncing is list-and-diff: `list(prefix)` minus the names already ingested, then `get` the rest.

| Plug | One batch is | Atomic publish | Create-only | `list(prefix)` |
|---|---|---|---|---|
| Manual (v1) | the whole full-state snapshot, one file | file write | each export is a fresh compacted snapshot | - |
| Synced folder (SAF) | a file | write `.tmp_*`, then rename | unique names; a retry resends identical bytes | list children, filter |
| WebDAV | a resource | `PUT`, or `PUT` temp + `MOVE` | `If-None-Match: *` | `PROPFIND Depth:1`, filter |
| Object storage (S3, R2, B2, MinIO) | an object | native | `If-None-Match: *` | `ListObjectsV2(prefix)` |
| Relay | a file on disk | relay does temp + rename | relay rejects existing names and checks the hash | relay filters by prefix |

The manual plug is a single-file export/import holding one full-state snapshot. Importing merges
`local ⊕ snapshot`; a snapshot is a delta over every field, so this exercises the same reducer.
The multi-object naming is first used at runtime by the folder and relay plugs.

### Relay

`server/`: one Ktor CIO binary, filesystem blob storage, manual wiring, no reflection (decision
0017). It implements the plug contract over HTTP and reuses the naming and contract types from
`core`. Cloudflare users need no relay: R2 works with the object-storage plug.

## Identity, pairing and recovery

- Identity is one 256-bit symmetric root secret (decision 0008), sourced behind
  `IdentityProvider` / `SyncConfig`: local key generation in v1, a sibling app later (federation,
  decision 0001).
- Pairing: one QR code carrying the root plus a checksum. Recovery: the root as BIP39 words, QR or
  file (decision 0011).
- Clients hold a set of roots keyed by `keyGen`, so rotation can come later without a format change.
- At rest: database under the OS sandbox and file-based encryption; the root as a KeyStore-wrapped
  blob (decision 0011).
