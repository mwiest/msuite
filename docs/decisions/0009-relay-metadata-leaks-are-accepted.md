# 0009: Relay metadata leaks are accepted; batch names stay as designed

Date: 2026-07-05. Status: accepted. Formerly OQ-9.

## Context

Even with E2E encryption, a backend learns things from what it stores and when.

## Decision

Keep the full batch name from 0003 and accept these leaks:

- Inherent to any backend: object size (roughly how much changed), timing and frequency of reads
  and writes, total volume, source IP or account, and linkage across apps or devices that share a
  backend.
- From the name: `type` (snapshot cadence), `deviceId` (device count, per-device profiles), and the
  HLC range, which gives the approximate wall-clock time of the edits themselves, including offline
  edits.

It cannot learn plaintext, field names or values, or the data-model version (encrypted header).
`hash` leaks nothing, not even duplicates: the per-batch random salt (0010) makes identical
content encrypt differently.

## Rejected

- Opaque random names: would lose decrypt-free enumeration, pruning and snapshot selection.

## Consequences

Consistent with the honest-but-curious operator in 0008. Mitigations that need no format change,
for later: padding plaintext into size buckets, per-app credentials or buckets, rotating
pseudonymous `deviceId`s. User side: Tor or a VPN, or self-host the relay.
