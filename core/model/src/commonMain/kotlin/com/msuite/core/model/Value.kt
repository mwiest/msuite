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

/** A typed field value; [ValueCodec] turns it into [Cell.value] bytes. */
sealed interface Value {
    data class Text(val value: String) : Value

    data class Bool(val value: Boolean) : Value

    data class Int64(val value: Long) : Value

    /** A calendar day without time or zone (decision 0052). */
    data class Date(val value: LocalDate) : Value
}

/** Each value is a one-field ProtoBuf message whose field number names the type. */
@OptIn(ExperimentalSerializationApi::class)
object ValueCodec {
    fun encode(value: Value): ByteArray = ProtoBuf.encodeToByteArray(
        when (value) {
            is Value.Text -> ValueProto(text = value.value)
            is Value.Bool -> ValueProto(bool = value.value)
            is Value.Int64 -> ValueProto(int64 = value.value)
            is Value.Date -> ValueProto(epochDays = value.value.toEpochDays())
        },
    )

    /** `null` for a type this app version doesn't know. */
    fun decode(bytes: ByteArray): Value? {
        val proto = ProtoBuf.decodeFromByteArray<ValueProto>(bytes)
        return proto.text?.let(Value::Text)
            ?: proto.bool?.let(Value::Bool)
            ?: proto.int64?.let(Value::Int64)
            ?: proto.epochDays?.let(::dateOrNull)
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
)
