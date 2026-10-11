# 0008: Identity is one symmetric root secret; the threat model's gaps are accepted

Date: 2026-07-05. Status: accepted. Formerly OQ-8.

## Context

No accounts, so identity has to be key material shared between a user's devices (and, by
federation, sibling apps). Pairing a device must work without a server.

## Decision

Identity is a single 256-bit symmetric root secret, no keypair. Pairing is one self-contained QR
code carrying the root plus a checksum; a recovery key is the camera-less fallback.

Protected:
- The relay or backend operator (honest-but-curious) sees ciphertext and metadata (0009). It can't
  read or forge batches (AEAD). It can withhold, delete or serve stale data; accepted for v1.
- The network sees only ciphertext (TLS plus E2E).

Accepted gaps:
- A lost or stolen device is a full compromise (past and future data). No revocation.
- No forward secrecy, no post-compromise security.
- Whoever captures the QR while it is shown gets the root (physical-trust model).
- A malicious sibling app gets the root if the user consents to the handoff.

The only answer to device loss is a manual rotation: new root, re-pair trusted devices, write under
the new root, abandon the old ciphertext. Built later. The door-opener built in v1: the batch
header carries a `keyGen` id, and clients hold a set of roots keyed by it.

## Rejected

- An asymmetric keypair / key agreement for pairing: not needed for a symmetric data path, and it
  would hit Android API gating for X25519/Ed25519 (0010).
- Forward secrecy and revocation: a long-offline device must still decrypt old batches, and
  revocation needs membership plus a re-key authority, which the dumb store doesn't have.

## Consequences

Simple, symmetric-only v1. Rotation fans out to every app on every device under federation,
O(devices × apps) re-pairs. It can't un-read data already synced.
