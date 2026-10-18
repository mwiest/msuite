package com.msuite.todo

import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder
import com.msuite.todo.domain.QrCodes
import com.msuite.todo.domain.QrMatrix
import java.nio.charset.StandardCharsets

/**
 * ZXing's QR encoder behind the domain seam (record 0074). The payload is binary, so ISO-8859-1
 * maps each byte to one character and the encoder emits it in byte mode unchanged; the encoder is
 * asked for modules rather than pixels, so nothing is scaled or padded here.
 */
class ZxingQrCodes : QrCodes {
    override fun encode(bytes: ByteArray): QrMatrix {
        val hints = mapOf(EncodeHintType.CHARACTER_SET to CHARSET)
        val encoded = Encoder.encode(String(bytes, StandardCharsets.ISO_8859_1), ErrorCorrectionLevel.M, hints)
        val modules = encoded.matrix
        val dark = BooleanArray(modules.width * modules.height)
        for (y in 0 until modules.height) {
            for (x in 0 until modules.width) {
                dark[y * modules.width + x] = modules.get(x, y).toInt() == 1
            }
        }
        return QrMatrix(modules.width, dark)
    }

    private companion object {
        /** Byte mode 1:1, the charset ZXing is told to use for the payload (record 0074). */
        const val CHARSET = "ISO-8859-1"
    }
}
