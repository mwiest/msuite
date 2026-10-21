package com.msuite.todo.domain

/**
 * The platform's QR codes, both halves (record 0074). It stays behind this seam (AGENTS.md): the
 * Android shell uses ZXing's encoder and zxing-android-embedded's camera, and a web build would use
 * `BarcodeDetector` (decision 0012).
 */
interface QrCodes {
    /** [bytes] as a QR: the pairing payload is a few dozen bytes, so this is no hot path. */
    fun encode(bytes: ByteArray): QrMatrix

    /**
     * Reads one code with the camera and returns its bytes, or `null` when the user backs out or
     * the camera yields nothing. The scanner reports its own failures, so the UI treats `null` as
     * "nothing scanned".
     */
    suspend fun scan(): ByteArray?
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
