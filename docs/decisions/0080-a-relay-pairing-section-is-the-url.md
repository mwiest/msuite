# 0080: A relay pairing section is the relay URL in UTF-8

Date: 2026-10-31. Status: accepted. Extends 0072.

## Context

Decision 0072 gives every pairing payload a backend type and a section whose layout that type
fixes. The relay is the first backend whose configuration is portable: a joining device must know
which relay the group uses, or it would sync into its own folder or nowhere. The relay's bearer
token is not part of this — it is derived from the root (0030).

## Decision

- `BackendType.Relay` has type id `2`. Its section is the relay's base URL as UTF-8, with no
  length prefix; the rest of the payload after the type byte is the section.
- `PairingPayload.build` takes a section for a relay and requires it to be an `http://` or
  `https://` URL. Manual and Folder carry no section. A known type with the wrong kind of section
  is rejected on parse; an unknown type keeps its section so an older reader still pairs.
- The joining device applies the descriptor after it adopts the root: a relay URL becomes its
  backend (a release build ignores an `http://` URL, 0079); any other known type clears the old
  backend, because a folder is picked per device (0072).
- No payload-format bump: 0072 already reserved the section for exactly this.

## Rejected

- A length-prefixed section: the section is the rest of the payload, and no other field follows.
- Carrying the relay token in the section: it is derivable from the root and would only widen what
  a photographed QR exposes.
- A separate QR type for each relay version: the URL is the whole configuration.

## Consequences

One QR moves the relay address with the identity, and an old build still reads the root and pairs,
ignoring the transport it doesn't know. A relay URL that moves later means showing a new QR. The
section is plain text in the QR; the URL was already visible on the sharing device.
