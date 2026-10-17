# 0071: The envelope's AAD is its header plus a context: a plug key or a manual label

Date: 2026-10-17. Status: accepted. Realizes the AAD of decision 0029 for the envelope of 0010.

## Context

0029 makes an object's key (minus its `hash`) part of the AAD, so a batch renamed into a snapshot,
moved into another namespace, or replayed from one app into another fails to decrypt. The manual
plug (0066) has no object key at all: it is a single file the user carries, so it has no key fields
to bind. Both paths still must not be able to accept each other's bytes, and the AAD has to be
reconstructable by a reader before it can decrypt.

## Decision

The AAD is the cleartext envelope header (envelope format, salt, data-model version, `keyGen`)
concatenated with a context:

- for a plug object, the whole object key except its trailing `hash`
  (`{appId}/{groupId}/v{fmt}_{type}_{deviceId}_{hlcLo}_{hlcHi}`, per 0029);
- for the manual snapshot file, the constant `msuite-manual-v1`.

A reader rebuilds the context from what it fetched: the key it listed for a plug object, or the
constant for the manual file, which has no key. Sealing and opening both take the context, so the
two paths cannot be crossed.

## Rejected

- No AAD for the manual file (the header alone): a manual file's header would authenticate against
  nothing name-like, so it could be fed to a plug path or vice versa and would still open.
- Store the object key inside the ciphertext and check it after decrypting: it hides the rename from
  the AEAD and only detects it with extra code, which is the same protection 0029 rejected once.
- A hash of the key as the only context: the reader would still need the key to compute it, so it is
  the key itself with an unnecessary indirection.

## Consequences

Renames, moves, cross-app replays and manual/plug swaps all fail as `MalformedBatch`, which
`decodeOrNull` already swallows (0068) and the corrupt flag counts (0069, invariant 17). The context
is a convention both ends share, not a secret; it leaks nothing the key does not already name. A
future plug with a different key grammar only has to pass that grammar's key minus its hash.
