# 0072: The pairing QR carries identity and the group's backend descriptor

Date: 2026-10-18. Status: accepted. Decides the open question of `plans/v1-todo.md` step 7; realizes
the QR of decision 0008.

## Context

0008 fixes identity as one 256-bit symmetric root in a single self-contained QR code with a
checksum, and leaves what else the code carries open: `v1-todo.md` defers "does the pairing QR also
carry the backend configuration?" to step 7. The joining device (0033) needs to end up on the same
transport as the group, and the plugs differ in what can travel with a scan: the manual snapshot
file has no configuration at all, a folder plug's SAF grant is bound to the granting device, and a
relay's URL plus namespace token are plain portable settings.

## Decision

The payload is, in one format version: a format byte, `keyGen`, the 256-bit root, a backend type, a
section whose layout that type fixes, and a checksum — the first 4 bytes of SHA-256 over everything
before it. Identity sits at fixed offsets, so a reader that doesn't know the backend type still
reads the root and pairs, reporting the unknown type instead of failing. There is no salt: the same
root and backend always produce the same QR, and a reader may compare the root to detect a scan of
its own code. The v1 build writes the type `manual` with an empty section; the folder type (step 8)
carries nothing either and makes the joining device offer its folder chooser; a backend with
portable configuration carries it in its section.

## Rejected

- Identity only, with the backend configured by hand on the joining device: it needs no format
  decision, but the joining device would have to be told the transport separately, and the folder
  type's chooser trigger would need a format change (or a re-scan) at step 8.
- Carrying a whole backend configuration blob now: nothing portable exists yet to put in it — the
  manual plug has no configuration and a SAF grant cannot move — so the blob would be empty or
  unusable, which is the deferred machinery 0034 rules out.
- A length-prefixed section: the type already fixes a section's layout, and the checksum already
  delimits the payload, so a length would let a reader accept a section whose layout it cannot
  check.
- Text (base32 or a URI) instead of a binary payload: 42 bytes of binary is one small QR, and the
  envelope already sets the binary+big-endian convention (0070).

## Consequences

One scan can pre-select the group's transport from the first step 7 build, even though v1 has only
one plug to name. A newer app's backend type doesn't block a join. A type that changes its section
is a payload format change — the format byte is the seam — and a new backend type id is not. The
payload is cleartext by necessity: the root is the secret being handed over, and whoever captures
the QR has it (0008's accepted gap). The checksum is a typo guard, not a signature.
