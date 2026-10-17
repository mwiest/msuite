# 0070: The data-model version lives in the envelope header, not the batch body

Date: 2026-10-17. Status: accepted. Supersedes the "one place only" clause of 0045 and closes the
open question in `v1-todo.md`.

## Context

0045 put the data-model version in the batch body, and 0067 makes `ingest` hold back a batch from a
newer version. Step 6 wraps the body in an encrypted envelope whose header is cleartext and
authenticated as AAD. If the version stayed in the body, a reader could only learn it *after*
decrypting, so `TooNew` would be undecidable for bytes it can't decrypt — and the version would sit
in two places (body and header) or in the wrong one.

## Decision

The envelope header carries the data-model version (with the salt and `keyGen`). The batch body —
the ProtoBuf message of 0045, now with field 1 reserved — carries cells only. `Batch.dataModelVersion`
is filled by the decoder: from the header for an envelope, or the current constant
(`Model.DATA_MODEL_VERSION`) for the plaintext transport Layers 1 and 2 keep. `ingest` keeps its
`TooNew` check (0067) unchanged.

## Rejected

- Keep the version in the body and mirror it in the header: two sources of truth, and the AAD would
  authenticate a copy that could disagree.
- Decrypt first, then read the version: `TooNew` is then unknown for a foreign or newer key, and a
  client could be forced to attempt decryption it should refuse.
- Version the whole envelope format separately from the data model: a naming-scheme version already
  rides in the object key (`v{fmt}`, 0029); a second counter adds no safety.

## Consequences

One place holds the version, and it is readable and authenticated before any key is derived, so
`TooNew` (0067) is decided cheaply. The plaintext transport can no longer express a newer version —
it always decodes as current — which is fine because it is test-only (Layers 1 and 2). A format
change to the body still needs the version bump this header carries.
