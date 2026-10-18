package com.msuite.todo.domain

/**
 * The platform's QR codes: drawing one now, and reading one in step 7 slice 5. It stays behind this
 * seam (AGENTS.md); the Android shell encodes with ZXing (record 0074), and a build without a camera
 * needs only [encode].
 */
interface QrCodes {
    /** [bytes] as a QR: the pairing payload is a few dozen bytes, so this is no hot path. */
    fun encode(bytes: ByteArray): QrMatrix
}

/** A QR as its dark modules: the shell's encoder produces it, the UI draws it (decision 0012). */
class QrMatrix(val size: Int, modules: BooleanArray) {
    private val dark = modules.copyOf()

    init {
        require(dark.size == size * size) { "a $size module QR takes ${size * size} modules, got ${dark.size}" }
    }

    /** Whether the module in column [x], row [y] is dark. */
    fun isDark(x: Int, y: Int): Boolean = dark[y * size + x]
}
