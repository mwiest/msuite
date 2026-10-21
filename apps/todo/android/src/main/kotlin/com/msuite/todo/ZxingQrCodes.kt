package com.msuite.todo

import androidx.activity.ComponentActivity
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import com.msuite.todo.domain.QrCodes
import com.msuite.todo.domain.QrMatrix
import kotlinx.coroutines.CompletableDeferred
import java.nio.charset.StandardCharsets

/**
 * ZXing behind the domain seam (record 0074): core encodes, zxing-android-embedded's camera scans
 * through the activity-result API, the way `SafSnapshotFiles` picks files. The shell builds it in
 * `onCreate`, before the activity is started, as `registerForActivityResult` requires.
 */
class ZxingQrCodes(private val activity: ComponentActivity) : QrCodes {
    private var pendingScan: CompletableDeferred<ByteArray?>? = null

    private val scanLauncher = activity.registerForActivityResult(ScanContract()) { result ->
        pendingScan?.complete(result.bytes())
        pendingScan = null
    }

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

    override suspend fun scan(): ByteArray? {
        val scanned = CompletableDeferred<ByteArray?>()
        // A replaced request fails instead of waiting on a camera that is already gone.
        pendingScan?.complete(null)
        pendingScan = scanned
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
        }
        scanLauncher.launch(options)
        return scanned.await()
    }

    /** The payload's bytes; the raw bytes keep byte mode 1:1, the string is the fallback. */
    private fun ScanIntentResult.bytes(): ByteArray? = rawBytes ?: contents?.toByteArray(StandardCharsets.ISO_8859_1)

    private companion object {
        /** Byte mode 1:1, the charset ZXing is told to use for the payload (record 0074). */
        const val CHARSET = "ISO-8859-1"
    }
}
