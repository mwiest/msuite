package com.msuite.core.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoIntegerType
import kotlinx.serialization.protobuf.ProtoNumber
import kotlinx.serialization.protobuf.ProtoType
import kotlin.uuid.Uuid

/** A typed field value; [ValueCodec] turns it into [Cell.value] bytes. */
sealed interface Value {
    data class Text(val value: String) : Value

    data class Bool(val value: Boolean) : Value

    data class Int64(val value: Long) : Value

    /** A calendar day without time or zone (decision 0052). */
    data class Date(val value: LocalDate) : Value

    /** Another record's ID (decision 0053). */
    data class Ref(val value: Uuid) : Value
}

/** Each value is a one-field ProtoBuf message whose field number names the type. */
@OptIn(ExperimentalSerializationApi::class)
object ValueCodec {
    /** `null` clears the field: empty bytes, which every reader decodes as absent (decision 0056). */
    fun encode(value: Value?): ByteArray = ProtoBuf.encodeToByteArray(
        when (value) {
            null -> ValueProto()
            is Value.Text -> ValueProto(text = value.value)
            is Value.Bool -> ValueProto(bool = value.value)
            is Value.Int64 -> ValueProto(int64 = value.value)
            is Value.Date -> ValueProto(epochDays = value.value.toEpochDays())
            is Value.Ref -> ValueProto(ref = value.value.toByteArray())
        },
    )

    /** `null` for a type this app version doesn't know, or for malformed bytes. */
    fun decode(bytes: ByteArray): Value? {
        // Malformed bytes (another device's bug, a damaged file) must not crash the reader; the
        // decoder throws more than SerializationException for them.
        val proto = runCatching { ProtoBuf.decodeFromByteArray<ValueProto>(bytes) }.getOrNull() ?: return null
        return proto.text?.let(Value::Text)
            ?: proto.bool?.let(Value::Bool)
            ?: proto.int64?.let(Value::Int64)
            ?: proto.epochDays?.let(::dateOrNull)
            ?: proto.ref?.takeIf { it.size == Uuid.SIZE_BYTES }?.let { Value.Ref(Uuid.fromByteArray(it)) }
    }

    // A day count beyond what LocalDate holds is as unreadable as an unknown type.
    private fun dateOrNull(epochDays: Long): Value.Date? =
        runCatching { Value.Date(LocalDate.fromEpochDays(epochDays)) }.getOrNull()
}

// The field numbers are the value types and can never change.
@OptIn(ExperimentalSerializationApi::class)
@Serializable
private class ValueProto(
    @ProtoNumber(1) val text: String? = null,
    @ProtoNumber(2) val bool: Boolean? = null,
    @ProtoNumber(3) @ProtoType(ProtoIntegerType.SIGNED) val int64: Long? = null,
    @ProtoNumber(4) @ProtoType(ProtoIntegerType.SIGNED) val epochDays: Long? = null,
    @ProtoNumber(5) val ref: ByteArray? = null,
)
