package com.msuite.core.sync

import com.msuite.core.model.Batch
import com.msuite.core.model.Cell
import com.msuite.core.model.CellKey
import com.msuite.core.model.Hlc
import com.msuite.core.model.Model

/** Merges a fetched batch into local state, unless it is too new or more than [HlcClock.MAX_AHEAD] ahead. */
fun ingest(batch: Batch, local: (CellKey) -> Cell?, clock: HlcClock): IngestResult {
    // A newer app version's batch may carry semantics this one doesn't know (decision 0067).
    if (batch.dataModelVersion > Model.DATA_MODEL_VERSION) return IngestResult.TooNew
    val hlcHi = batch.hlcHi()
    return when {
        hlcHi == null -> IngestResult.Merged(emptyList())

        !clock.accepts(hlcHi) -> IngestResult.Deferred

        else -> {
            clock.observe(hlcHi)
            IngestResult.Merged(merge(local, batch.cells))
        }
    }
}

sealed interface IngestResult {
    /** Too far in the future (decision 0040): don't mark it ingested, retry on a later sync. */
    data object Deferred : IngestResult

    /** From a newer data-model version than this app knows (decision 0067): retry after an update. */
    data object TooNew : IngestResult

    /** Sealed under a root generation this device doesn't hold (decision 0008): skip it. */
    data object UnknownKey : IngestResult

    /** [changes] are the cells to write into local state. */
    data class Merged(val changes: List<Cell>) : IngestResult
}

/** The outgoing delta for a plug (decision 0036): every cell newer than its [watermark], or `null`. */
fun buildDelta(cells: Iterable<Cell>, watermark: Hlc?): Batch? {
    val newer = cells.filter { watermark == null || it.stamp.hlc > watermark }
    return if (newer.isEmpty()) null else Batch(Model.DATA_MODEL_VERSION, newer)
}

/** The highest HLC in the batch: its `hlcHi` (decision 0029), and the plug's next watermark. */
fun Batch.hlcHi(): Hlc? = cells.maxOfOrNull { it.stamp.hlc }
