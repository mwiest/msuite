# 0029: Batch keys are namespaced per app and group, sortable, and authenticated

Date: 2026-10-11. Status: accepted. Replaces the name grammar of 0003 and extends the AAD of 0010.

## Context

Three gaps in the batch names of 0003:

- The name fields (`type`, `deviceId`, HLC range) were not covered by the encryption. Clients
  prune and pick snapshots by name without decrypting, so a relay could rename a delta into a
  wide-range snapshot and make clients skip real deltas: silent data loss.
- Names had no namespace. Two users on one relay, or two apps in one bucket or folder, would list
  each other's batches.
- RFC 4648 base32 (`A-Z2-7`) does not sort in ASCII order, so HLC fields encoded with it don't
  sort lexicographically.

## Decision

A batch is stored under the object key `{appId}/{groupId}/{name}`; `{appId}/{groupId}` is the
namespace.

- `appId` is the app's short lowercase name, a constant in code (`todo`, `notes`, ...).
- `groupId` = the first 128 bits of `HKDF-SHA256(root, info = "msuite-group-v1")`, 26 characters.
  It is the same for all apps of a group and reveals nothing about the root.
- `name` = `v{fmt}_{type}_{deviceId}_{hlcLo}_{hlcHi}_{hash}`, as before.
- All binary fields (`deviceId`, HLCs, `hash`) are uppercase **base32hex** (RFC 4648 extended hex,
  `0-9A-V`), unpadded and fixed width, so they sort in value order. An HLC is 64 bits
  (48-bit millisecond `pt`, 16-bit counter `c`), 13 characters.
- The AAD is the batch header (`salt`, data-model version, `keyGen`) plus the whole object key
  except `hash`, which is computed over the ciphertext and can't be inside it. A reader rebuilds
  the AAD from the key it fetched, so a renamed or moved batch fails to decrypt.
- Plugs map the namespace to their container: nested subfolders in the synced folder, a key prefix
  in S3, path segments in WebDAV and on the relay.

## Rejected

- One opaque segment per app and group (`HKDF(root, "msuite-ns-v1:" + appId)`): hides which app a
  namespace belongs to and doesn't link a user's apps, but keys are harder to read when inspecting
  or debugging a backend. Readability won.
- Checking name fields against a copy inside the plaintext: the same protection with more code.

## Consequences

Renaming, moving or replaying a batch into another namespace, including another app's, is
detected. A new root (rotation, 0008) means a new `groupId`, which matches "abandon the old
ciphertext". A backend sees which apps a group uses and that they belong together (0009 accepts
this kind of metadata); per-app opaque namespaces remain a possible mitigation.
