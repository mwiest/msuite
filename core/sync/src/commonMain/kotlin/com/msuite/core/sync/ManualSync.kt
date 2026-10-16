package com.msuite.core.sync

import com.msuite.core.model.Batch
import com.msuite.core.model.Model
import com.msuite.core.model.ProtoBatchEncoder

/**
 * The manual plug (decision 0066): the user exports the whole state to one file and imports a file
 * on another device. There is no key and no list; the user is the transport. Until step 6 the
 * export is plaintext, so the path is a proof of concept until step 6 encrypts it.
 */
class ManualSync(private val replica: Replica) {
    /** The whole local state, encoded. */
    suspend fun export(): ByteArray = ProtoBatchEncoder.encode(Batch(Model.DATA_MODEL_VERSION, replica.cells()))

    /**
     * Decodes [bytes] and merges them in. Throws [com.msuite.core.model.MalformedBatch] if they are
     * not a snapshot; a snapshot from a clock far ahead returns [IngestResult.Deferred].
     */
    suspend fun import(bytes: ByteArray): IngestResult = replica.mergeRemote(ProtoBatchEncoder.decode(bytes))
}
