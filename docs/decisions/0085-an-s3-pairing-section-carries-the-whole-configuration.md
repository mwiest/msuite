# 0085: An S3 pairing section carries the whole configuration, secret included

Date: 2026-11-10. Status: accepted. Extends 0072, alongside 0080.

## Context

Decision 0072 gives every pairing payload a backend type and a section whose layout that type
fixes. Unlike the relay (0080), an S3 bucket needs several fields to reach (endpoint, region,
bucket, an optional key prefix) and a credential the root can't derive: an access key ID and its
secret. Typing a 40- or 64-character secret on a phone is the setup step most likely to fail.

## Decision

- `BackendType.S3` has type id `3`. Its section is an `S3Section` encoded with kotlinx ProtoBuf
  (decision 0016): endpoint (1), region (2), bucket (3), prefix (4, omitted when empty), access key
  ID (5) and secret (6). The field numbers are pinned by a test and never change; a field added
  later gets a new number, and an older reader skips it.
- The secret travels in the QR. Whoever sees the QR already holds the root, which opens every
  batch; the credential only adds access to a bucket that holds ciphertext.
- A section is usable when the endpoint is `http(s)://` and region, bucket and both key parts are
  non-blank; anything else fails the parse, as a bad relay URL does. A release build will refuse an
  `http://` endpoint when the app applies it, as for the relay (0079).
- No payload-format bump: 0072 reserved the section for this.

## Rejected

- Leaving the secret out of the QR and typing it on each device: a long random string typed by hand,
  for no gain while the QR carries the root.
- A length-prefixed field list of our own: a new mini-format, where adding a field needs a format
  bump. ProtoBuf is already in the stack.
- Deriving the S3 credential from the root: S3 providers issue their own keys; we can't choose them.

## Consequences

One scan sets up a second device completely. A photographed QR now also grants write and delete
access to the bucket, on top of reading the data: the user should give the key access to that
bucket only, and rotating the key means showing a new QR. A QR with an R2 key is about 230 bytes,
well inside what the pairing screen renders; slice 6's device check confirms it scans.
