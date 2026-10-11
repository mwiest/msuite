package com.msuite.core.storage

/**
 * Placeholder for local persistence.
 *
 * Responsibilities (see docs/architecture.md -> Modules):
 *  - Op-log store + materialized current-state view with per-field (value, HLC, deviceId)
 *  - Opaque retention of unknown field IDs (decision 0006 forward-compat)
 *  - At rest: OS sandbox + FBE; root stored KeyStore-wrapped (decision 0011)
 *
 * Implementation candidate: SQLDelight (KMP: android, jvm) — not yet wired.
 */
object Storage
