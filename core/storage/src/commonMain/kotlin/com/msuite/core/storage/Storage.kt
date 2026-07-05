package com.msuite.core.storage

/**
 * Placeholder for local persistence.
 *
 * Responsibilities (see msuite-v1.md -> core responsibilities / storage):
 *  - Op-log store + materialized current-state view with per-field (value, HLC, deviceId)
 *  - Opaque retention of unknown field IDs (OQ-6 forward-compat)
 *  - At rest: OS sandbox + FBE; root stored KeyStore-wrapped (OQ-11)
 *
 * Implementation candidate: SQLDelight (KMP: android, jvm) — not yet wired.
 */
object Storage
