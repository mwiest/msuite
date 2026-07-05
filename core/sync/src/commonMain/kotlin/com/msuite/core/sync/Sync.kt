package com.msuite.core.sync

/**
 * Placeholder for the sync engine.
 *
 * Responsibilities (see msuite-v1.md -> core responsibilities / sync):
 *  - HLC clock, op-log append/merge
 *  - LWW reducer behind a `merge()` interface (total order (pt, c, deviceId) — OQ-5)
 *  - Backend-plug interface (put/list/get) + content-addressed batch naming + adapters
 *
 * Correctness is gated by the property-based merge/HLC simulator (OQ-7), which runs
 * on the jvm target — see src/jvmTest.
 */
object Sync
