package com.msuite.server

/**
 * Placeholder entry point for the dumb relay.
 *
 * The real relay is a single Ktor (CIO) binary implementing put/list/get(/delete)
 * with create-only atomic publish + hash verification, filesystem-backed and
 * reflection-averse for a cheap GraalVM-native build (see msuite.md -> Backend plugs).
 */
fun main() {
    println("msuite relay skeleton — Ktor CIO server pending (see msuite-v1.md step 8).")
}
