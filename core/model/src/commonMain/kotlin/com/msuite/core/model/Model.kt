package com.msuite.core.model

/**
 * Placeholder for shared domain types + op definitions.
 *
 * Responsibilities (see msuite-v1.md -> core responsibilities / model):
 *  - Domain types, stable record IDs (UUID) + stable opaque field/op IDs (OQ-6)
 *  - Op definitions and the data-model version header
 *  - The `upcast` hook (identity for now — single version)
 *  - Batch/snapshot encoding behind an encoder interface (kotlinx.serialization + ProtoBuf)
 */
object Model {
    /** Data-model version carried in each batch's (encrypted) header — OQ-6. */
    const val DATA_MODEL_VERSION: Int = 1
}
