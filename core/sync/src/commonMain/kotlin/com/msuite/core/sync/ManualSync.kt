package com.msuite.core.sync

import com.msuite.core.crypto.BatchEnvelope
import com.msuite.core.crypto.EnvelopeResult
import com.msuite.core.crypto.IdentityProvider
import com.msuite.core.crypto.KEY_GEN_V1
import com.msuite.core.model.Batch
import com.msuite.core.model.BatchEncoder
import com.msuite.core.model.Model
import com.msuite.core.model.ProtoBatchEncoder

/** The manual file has no object key, so its AAD context is this constant (decisions 0029, 0071). */
const val MANUAL_AAD_CONTEXT = "msuite-manual-v1"

/**
 * The manual plug (decision 0066): the user exports the whole state to one encrypted file and
 * imports a file on another device. There is no key and no list; the user is the transport. The
 * export is a [BatchEnvelope] under [MANUAL_AAD_CONTEXT], so it only imports back on a device that
 * holds the same root — until pairing carries the root over (v1 step 7).
 */
class ManualSync(
    private val replica: Replica,
    identity: IdentityProvider,
    encoder: BatchEncoder = ProtoBatchEncoder,
) {
    private val envelope = BatchEnvelope(encoder, identity::root, KEY_GEN_V1)

    /** The whole local state, sealed into one envelope. */
    suspend fun export(): ByteArray =
        envelope.seal(Batch(Model.DATA_MODEL_VERSION, replica.cells()), MANUAL_AAD_CONTEXT)

    /**
     * Opens [bytes] and merges them in. Throws [com.msuite.core.model.MalformedBatch] if they are
     * not a readable envelope (torn, tampered, or a foreign root); reports [IngestResult.TooNew]
     * and [IngestResult.UnknownKey] from the header without decrypting (decisions 0067, 0008).
     */
    suspend fun import(bytes: ByteArray): IngestResult = when (val result = envelope.open(bytes, MANUAL_AAD_CONTEXT)) {
        is EnvelopeResult.Opened -> replica.mergeRemote(result.batch)
        EnvelopeResult.TooNew -> IngestResult.TooNew
        EnvelopeResult.UnknownKeyGen -> IngestResult.UnknownKey
    }
}
