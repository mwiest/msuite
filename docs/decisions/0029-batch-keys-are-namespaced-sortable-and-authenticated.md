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

A batch is stored under the object key `{namespace}/{name}`.

- `namespace` = the first 128 bits of `HKDF-SHA256(root, info = "msuite-ns-v1:" + appId)`, 26
  characters. It is one opaque path segment per app and group: it reveals neither the app nor the
  root, and two apps of the same user don't share a visible identifier.
- `name` = `v{fmt}_{type}_{deviceId}_{hlcLo}_{hlcHi}_{hash}`, as before.
- All binary fields (`deviceId`, HLCs, `hash`) are uppercase **base32hex** (RFC 4648 extended hex,
  `0-9A-V`), unpadded and fixed width, so they sort in value order. An HLC is 64 bits
  (48-bit millisecond `pt`, 16-bit counter `c`), 13 characters.
- The AAD is the batch header (`salt`, data-model version, `keyGen`) plus the whole object key
  except `hash`, which is computed over the ciphertext and can't be inside it. A reader rebuilds
  the AAD from the key it fetched, so a renamed or moved batch fails to decrypt.
- Plugs map `namespace` to their container: a subfolder in the synced folder, a key prefix in S3, a
  path segment in WebDAV and on the relay.

## Rejected

- `{appId}/{groupId}/`: names the app in clear text and links a user's apps.
- Checking name fields against a copy inside the plaintext: the same protection with more code.

## Consequences

Renaming, moving or replaying a batch into another namespace is detected. A new root (rotation,
0008) means a new namespace, which matches "abandon the old ciphertext". The namespace still leaks
that one group uses one app (0009 accepts that).
