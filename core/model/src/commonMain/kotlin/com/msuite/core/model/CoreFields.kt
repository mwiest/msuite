package com.msuite.core.model

/** Field IDs every app's records share (decision 0054). App fields start above [RESERVED]. */
object CoreFields {
    private const val LAST_RESERVED = 9
    val RESERVED: IntRange = 1..LAST_RESERVED

    /** The record's type: an app-defined `Value.Int64` type ID, written when the record is created. */
    val TYPE = FieldId(1)

    /** The tombstone: `Value.Bool`, `true` once the record is deleted. */
    val DELETED = FieldId(2)

    val ALL: List<FieldId> = listOf(TYPE, DELETED)
}
