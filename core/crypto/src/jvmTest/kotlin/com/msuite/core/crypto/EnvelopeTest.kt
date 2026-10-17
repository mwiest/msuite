package com.msuite.core.crypto

import com.msuite.core.model.Batch
import com.msuite.core.model.Cell
import com.msuite.core.model.CellKey
import com.msuite.core.model.DeviceId
import com.msuite.core.model.FieldId
import com.msuite.core.model.Hlc
import com.msuite.core.model.MalformedBatch
import com.msuite.core.model.Model
import com.msuite.core.model.ProtoBatchEncoder
import com.msuite.core.model.Stamp
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlin.uuid.Uuid

private const val CONTEXT = "msuite-manual-v1"

private val root = ByteArray(32) { it.toByte() }
private val otherRoot = ByteArray(32) { (it + 1).toByte() }

private fun envelope(keyGen: Int = KEY_GEN_V1, roots: (Int) -> ByteArray? = { if (it == KEY_GEN_V1) root else null }) =
    BatchEnvelope(ProtoBatchEncoder, roots, keyGen)

private fun batch(version: Int = Model.DATA_MODEL_VERSION, value: Byte = 1) = Batch(
    version,
    listOf(Cell(CellKey(Uuid.fromLongs(7, 8), FieldId(1)), byteArrayOf(value), Stamp(Hlc.of(5, 0), DeviceId(1)))),
)

class EnvelopeTest : StringSpec({
    "an envelope round-trips under its root" {
        val sealed = envelope().seal(batch(), CONTEXT)
        val opened = envelope().open(sealed, CONTEXT) as EnvelopeResult.Opened
        opened.batch.dataModelVersion shouldBe Model.DATA_MODEL_VERSION
        opened.batch.cells shouldContainExactly batch().cells
    }

    "the version rides in the header: a newer one is TooNew without decrypting" {
        val sealed = envelope().seal(batch(version = Model.DATA_MODEL_VERSION + 1), CONTEXT)
        // A roots function that would throw proves the header is read first.
        envelope(roots = { error("decrypted a too-new batch") }).open(sealed, CONTEXT) shouldBe EnvelopeResult.TooNew
    }

    "an unknown keyGen is reported without decrypting" {
        val sealed = envelope(keyGen = KEY_GEN_V1 + 1, roots = { root }).seal(batch(), CONTEXT)
        envelope().open(sealed, CONTEXT) shouldBe EnvelopeResult.UnknownKeyGen
    }

    "a renamed or moved batch fails to open" {
        val sealed = envelope().seal(batch(), CONTEXT)
        shouldThrow<MalformedBatch> { envelope().open(sealed, "todo/group/v1_d_x_hash") }
    }

    "a foreign root fails to open" {
        val sealed = envelope(roots = { otherRoot }).seal(batch(), CONTEXT)
        shouldThrow<MalformedBatch> { envelope().open(sealed, CONTEXT) }
    }

    "a tampered byte fails to open" {
        val sealed = envelope().seal(batch(), CONTEXT)
        sealed[sealed.size - 1] = (sealed[sealed.size - 1].toInt() xor 1).toByte()
        shouldThrow<MalformedBatch> { envelope().open(sealed, CONTEXT) }
    }

    "bytes that aren't an envelope are rejected" {
        shouldThrow<MalformedBatch> { envelope().open(byteArrayOf(0x08), CONTEXT) }
        shouldThrow<MalformedBatch> { envelope().open(ByteArray(41), CONTEXT) }
    }

    "the header layout is pinned" {
        val salt = ByteArray(SECRET_SIZE) { it.toByte() }
        val header = envelope().seal(batch(), CONTEXT, salt).copyOfRange(0, 41)
        header.hex() shouldBe
            "01" + // envelope format
            "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f" + // salt
            "00000001" + // data-model version
            "00000001" // keyGen
    }
})

private fun ByteArray.hex(): String = joinToString("") { it.toUByte().toString(16).padStart(2, '0') }
