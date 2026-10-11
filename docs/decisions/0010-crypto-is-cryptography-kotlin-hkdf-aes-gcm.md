# 0010: Crypto is cryptography-kotlin, per-batch HKDF into AES-256-GCM

Date: 2026-07-05. Status: accepted; AAD extended by 0029. Formerly OQ-10.

## Context

Batches need confidentiality, integrity and authenticity within the group, in Kotlin, without
native code, without Google libraries, and portable to web later.

## Decision

Library: **cryptography-kotlin** (whyoleg) behind `core/crypto`. It wraps vetted platform providers
(JDK/Conscrypt on Android, WebCrypto on web). Apache-2, so vendorable and swappable.

Scheme:
- Per batch: random 256-bit `salt`, `K = HKDF-SHA256(root, salt, "msuite-batch-v1")`, then
  `AES-256-GCM(K, fixed nonce, plaintext, aad = header)`. A fresh key per batch makes nonce reuse
  impossible whatever the nonce width. ChaCha20-Poly1305 is an acceptable alternative AEAD.
- The header (`salt`, data-model version (0006), `keyGen` id (0008)) is authenticated as AAD.
- Content address (0003): SHA-256 over the ciphertext.
- Recovery: the root as BIP39-style 24 words or base32.
- Don't roll your own: all primitives come from the library; our code only composes them.

## Rejected

- Tink: FOSS, but Google, and JVM-only.
- libsodium: native `.so`. Fallback if a primitive or misuse-resistance gap appears.

## Consequences

The library is younger and has one maintainer; the risk is bounded by the interface. Its JDK
provider inherits Android's API gating for X25519/Ed25519 (API 31/33+), which the symmetric-only
path avoids. If asymmetric crypto is ever needed on older Android, add a BouncyCastle-backed
provider.
