package com.msuite.core.crypto

/**
 * Placeholder for the crypto layer.
 *
 * Responsibilities (see docs/architecture.md -> Modules):
 *  - 256-bit symmetric root (identity, no keypair — decision 0008)
 *  - Per-batch HKDF-SHA256(root, salt) -> AES-256-GCM with authenticated header
 *  - Single-QR pairing payload (carries the root); BIP39 recovery encode/decode
 *
 * Library: cryptography-kotlin (whyoleg) behind this interface (decision 0010) — not yet wired.
 */
object Crypto
