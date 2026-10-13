package com.msuite.core.model

import kotlin.jvm.JvmInline
import kotlin.uuid.Uuid

/** A stable field ID (decisions 0006, 0043); also the proto field number (decision 0016). */
@JvmInline
value class FieldId(val id: Int)

data class CellKey(val recordId: Uuid, val fieldId: FieldId)

/** Who wrote a cell, and when: the total order `(pt, c, deviceId)` of LWW (decision 0005). */
data class Stamp(val hlc: Hlc, val deviceId: DeviceId) : Comparable<Stamp> {
    override fun compareTo(other: Stamp): Int = compareValuesBy(this, other, { it.hlc }, { it.deviceId })
}

/** One `field_state` row (decision 0035). [value] is encoded by the batch encoder; merge treats it as opaque. */
class Cell(val key: CellKey, val value: ByteArray, val stamp: Stamp) {
    override fun equals(other: Any?): Boolean =
        other is Cell && key == other.key && stamp == other.stamp && value.contentEquals(other.value)

    override fun hashCode(): Int = (key.hashCode() * 31 + stamp.hashCode()) * 31 + value.contentHashCode()

    override fun toString(): String = "Cell($key, ${value.contentToString()}, $stamp)"
}
