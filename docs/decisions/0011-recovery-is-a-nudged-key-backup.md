# 0011: Recovery is a nudged key backup; data at rest relies on the OS and KeyStore

Date: 2026-07-05. Status: accepted. Formerly OQ-11.

## Context

Without accounts there is no password reset. A recovery key only recovers the key, not data: with
two devices or a backend, losing one device is a non-event; with one device and no backend, no key
scheme helps.

## Decision

Recovery:
- The main durability push is "pair a second device or set up a backend". The recovery key covers
  the all-devices-lost case.
- No key ceremony at setup. A contextual nudge fires when durability starts to matter (when a
  backend is configured, or still single-device after a few days), with honest copy.
- The root is offered as words (BIP39), QR or file, and copy-to-clipboard, framed as "save it to
  your password manager".

At rest:
- Database and op-log: the OS sandbox plus file-based encryption.
- Root secret: stored as a hardware-backed KeyStore-wrapped blob. The root stays exportable (0008);
  only the device-local wrapping key is a non-exportable KeyStore key.

## Rejected

- Passphrase-based recovery (passphrase-wrapped root on the backend, Argon2id): familiar, but adds
  a password, weakens "no account", needs a backend. Deferred behind the recovery interface.
- SQLCipher: resists rooted-device extraction but needs a native `.so`. Deferred, opt-in later
  behind the storage layer.

## Consequences

No native code in v1. Data on a rooted or forensically extracted device is not protected beyond
what the OS gives.
