# 0045: A batch is a sorted list of cells; a value is a typed ProtoBuf wrapper

Date: 2026-10-13. Status: accepted; value types extended by 0052. Details the encoding of 0016.

## Context

0016 picks kotlinx.serialization ProtoBuf, and 0035 stores each value as encoded bytes, but neither
fixes the batch layout or how a typed value becomes bytes. Both end up in stored data.

## Decision

- A batch is `{ dataModelVersion = 1, cells = 2 }`. A cell is `{ recordId = 1 (16 bytes),
  fieldId = 2, value = 3 (bytes), hlc = 4 (fixed64), deviceId = 5 (fixed64) }`. Cells are sorted
  by `(recordId, fieldId)` with at most one per key, so a batch always encodes to the same bytes.
  Deltas and snapshots share the message; the type is only in the object key (0029).
- A value is a one-field ProtoBuf message whose field number names its type: `text = 1`,
  `bool = 2`, `int64 = 3` (zigzag). New types get new numbers; a reader that doesn't know a type
  decodes the value as absent.
- Until the envelope of step 6 exists, the data-model version is in the batch; step 6 keeps it in
  one place only.
- Unknown proto fields are skipped on decode, so newer writers can add fields.

## Rejected

- A hand-written value codec (UTF-8 text, one byte per boolean): smaller, but more of our own
  code, and the bytes don't say which type they hold.
- Separate delta and snapshot messages: same content, more code.

## Consequences

One library and one style for batches and values. Each value costs a tag byte and a length. A
field can change type safely: an old reader sees a value of a type it doesn't know and treats it
as absent instead of misreading it. Pinned-bytes tests guard determinism (0016).
