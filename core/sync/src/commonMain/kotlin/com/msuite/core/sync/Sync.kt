package com.msuite.core.sync

/**
 * Placeholder for the sync engine.
 *
 * Responsibilities (see docs/architecture.md -> Modules):
 *  - HLC clock, op-log append/merge
 *  - LWW reducer behind a `merge()` interface (total order (pt, c, deviceId) — decision 0005)
 *  - Backend-plug interface (put/list/get) + content-addressed batch naming + adapters
 *
 * Correctness is gated by the property-based merge/HLC simulator (decision 0007), which runs
 * on the jvm target — see src/jvmTest.
 */
object Sync
