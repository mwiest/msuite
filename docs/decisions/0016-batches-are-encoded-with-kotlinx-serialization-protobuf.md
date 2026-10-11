# 0016: Batches and snapshots are encoded with kotlinx.serialization ProtoBuf

Date: 2026-07-05. Status: accepted.

## Context

Op-batches and the export snapshot need a compact, deterministic binary encoding that works on
android and jvm (and web later), and that carries the stable field IDs of 0006.

## Decision

kotlinx.serialization with its ProtoBuf format, behind an encoder interface in `core/model`.

- Proto field numbers are the stable opaque IDs of 0006: renames are non-events.
- Unknown fields are modelled explicitly as `(id, bytes, HLC, deviceId)` state entries, not left to
  protobuf's unknown-field retention, so no `.proto` schema toolchain is needed.
- Encoding is deterministic (canonical field and map order, which we control): the same logical
  batch gives the same bytes, for idempotent retries (0003) and reproducible tests.
- A human-readable JSON debug view may be added behind the same interface.

## Rejected

- Square Wire: an extra schema toolchain we don't need.
- JSON as the primary format: larger, which also widens the size leak (0009).

## Consequences

One KMP-native encoder for every target. Determinism is our job, not the library's: tests must
pin it.
