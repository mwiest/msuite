package com.msuite.todo.domain

import com.msuite.core.model.FieldId
import kotlin.uuid.Uuid

// IDs, type IDs and field IDs are stored data: they can never change, and removed ones stay reserved
// (decisions 0006, 0043, 0054).

/** Records that exist once per app (decision 0031). */
object TodoIds {
    /** The default list, created on first run on every device. */
    val MY_TASKS: Uuid = Uuid.parse("fe8696e3-4e7f-4d53-a464-6d0da4f50a4f")
}

/** Values of `CoreFields.TYPE`. */
object TodoTypes {
    const val LIST: Long = 1
    const val TASK: Long = 2
}

@Suppress("MagicNumber") // the numbers are the IDs
object TodoFields {
    /** List and task: `Text`. */
    val TITLE = FieldId(10)

    /** List and task: `Text`, a fractional index (decisions 0004, 0044). */
    val POSITION = FieldId(11)

    /** Task: `Ref` to its list. */
    val LIST = FieldId(12)

    /** Task: `Text`. */
    val NOTES = FieldId(13)

    /** Task: `Date`. */
    val DUE = FieldId(14)

    /** Task: `Bool`. */
    val STARRED = FieldId(15)

    /** Task: `Bool`. */
    val DONE = FieldId(16)
}
