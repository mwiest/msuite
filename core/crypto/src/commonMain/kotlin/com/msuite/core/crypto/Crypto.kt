package com.msuite.core.crypto

/**
 * Placeholder for the crypto layer.
 *
 * Responsibilities (see msuite-v1.md -> core responsibilities / crypto):
 *  - 256-bit symmetric root (identity, no keypair — OQ-8)
 *  - Per-batch HKDF-SHA256(root, salt) -> AES-256-GCM with authenticated header
 *  - Single-QR pairing payload (carries the root); BIP39 recovery encode/decode
 *
 * Library: cryptography-kotlin (whyoleg) behind this interface (OQ-10) — not yet wired.
 */
object Crypto
