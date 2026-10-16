package com.msuite.core.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoIntegerType
import kotlinx.serialization.protobuf.ProtoNumber
import kotlinx.serialization.protobuf.ProtoType
import kotlin.uuid.Uuid

/** The cells of one delta or snapshot; the type is in the object key only (decision 0029). */
class Batch(val dataModelVersion: Int, cells: List<Cell>) {
    val cells: List<Cell> = cells.sortedWith(CELL_ORDER)

    init {
        require(this.cells.zipWithNext().none { (a, b) -> a.key == b.key }) { "two cells for one key" }
    }

    companion object {
        /** Canonical order, so the same batch always encodes to the same bytes (decision 0016). */
        val CELL_ORDER: Comparator<Cell> = compareBy<Cell> { it.key.recordId }.thenBy { it.key.fieldId.id }
    }
}

interface BatchEncoder {
    fun encode(batch: Batch): ByteArray

    /** @throws MalformedBatch if [bytes] are not a batch this encoder wrote. */
    fun decode(bytes: ByteArray): Batch
}

/** Thrown by [BatchEncoder.decode] on bytes it can't read. A caller skips the batch and retries it later (0068). */
class MalformedBatch(cause: Throwable? = null) : Exception("not an encoded batch", cause)

/** [decode], or `null` if the bytes can't be read: the caller skips the batch and retries it later (0068). */
fun BatchEncoder.decodeOrNull(bytes: ByteArray): Batch? = try {
    decode(bytes)
} catch (ignored: MalformedBatch) {
    null
}

/** kotlinx.serialization ProtoBuf (decision 0016). The proto field numbers below can never change. */
@OptIn(ExperimentalSerializationApi::class)
object ProtoBatchEncoder : BatchEncoder {
    override fun encode(batch: Batch): ByteArray = ProtoBuf.encodeToByteArray(
        BatchProto(batch.dataModelVersion, batch.cells.map { it.toProto() }),
    )

    override fun decode(bytes: ByteArray): Batch = try {
        val proto = ProtoBuf.decodeFromByteArray<BatchProto>(bytes)
        Batch(proto.dataModelVersion, proto.cells.map { it.toCell() })
    } catch (e: IllegalArgumentException) {
        throw MalformedBatch(e)
    }

    private fun Cell.toProto() = CellProto(
        recordId = key.recordId.toByteArray(),
        fieldId = key.fieldId.id,
        value = value,
        hlc = stamp.hlc.packed,
        deviceId = stamp.deviceId.bits,
    )

    private fun CellProto.toCell() = Cell(
        key = CellKey(Uuid.fromByteArray(recordId), FieldId(fieldId)),
        value = value,
        stamp = Stamp(Hlc.fromPacked(hlc), DeviceId(deviceId)),
    )
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
private class BatchProto(
    @ProtoNumber(1) val dataModelVersion: Int,
    @ProtoNumber(2) val cells: List<CellProto> = emptyList(),
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
private class CellProto(
    @ProtoNumber(1) val recordId: ByteArray,
    @ProtoNumber(2) val fieldId: Int,
    @ProtoNumber(3) val value: ByteArray,
    @ProtoNumber(4) @ProtoType(ProtoIntegerType.FIXED) val hlc: Long,
    @ProtoNumber(5) @ProtoType(ProtoIntegerType.FIXED) val deviceId: Long,
)
