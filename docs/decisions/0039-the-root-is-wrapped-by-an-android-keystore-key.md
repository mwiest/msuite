# 0039: The root at rest is wrapped by an Android KeyStore key directly

Date: 2026-10-11. Status: accepted. Replaces "Jetpack Security / `EncryptedSharedPreferences`"
in 0011.

## Context

0011 stores the root as a KeyStore-wrapped blob using Jetpack Security, but
`androidx.security:security-crypto` (`EncryptedSharedPreferences`) has been deprecated.

## Decision

A non-exportable AES-256-GCM key in the Android KeyStore (hardware-backed where available) wraps
the root. The wrapped root (IV plus ciphertext) is stored in a file in the app's private storage.
Plain JCA, about 40 lines, no dependency. It sits behind the at-rest interface of the app layer,
so web can use its own implementation (0012).

## Rejected

- Jetpack Security: deprecated.
- Tink's Android key manager: Google, against 0010.

## Consequences

No dependency for the at-rest path. We own a small piece of KeyStore code that needs a device test.
The root itself stays exportable for pairing and recovery (0008).
