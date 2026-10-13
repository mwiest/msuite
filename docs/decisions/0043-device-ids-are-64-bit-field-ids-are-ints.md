# 0043: Device IDs are random 64-bit values, field IDs are Ints, a full HLC counter carries

Date: 2026-10-13. Status: accepted.

## Context

The merge core (v1 step 2) fixes three representations that end up in `field_state`, in batches
and in object keys, so they can't change later without a format change.

## Decision

- A `deviceId` is a random 64-bit value, ordered unsigned (so it sorts like its 13-character
  base32hex key field, 0029). It breaks HLC ties in `(pt, c, deviceId)` (0005).
- A stable field ID (0006, 0035) is an `Int`, the proto field number of 0016. Reserved IDs (record
  type, `deleted`) are small constants in code.
- When the HLC's 16-bit counter is full, `increment` carries into `pt` (1 ms ahead) instead of
  failing. The 24 h clamp (0040) bounds the drift.

## Rejected

- `deviceId` as a 128-bit `Uuid`: the same type as record IDs and collision-free, but 26 key
  characters and twice the bytes per cell. 64 random bits are enough for the handful of devices
  in a group.
- Field IDs as short strings: readable in a dump, but bigger, and they invite treating names as
  meaningful (against 0006).
- Throwing on counter overflow (textbook HLC): a crash path that real use never hits but a
  simulator might.

## Consequences

Keys and cells stay compact. A `deviceId` collision within one group would break the
single-writer assumption of 0003; at 64 bits that is negligible.
