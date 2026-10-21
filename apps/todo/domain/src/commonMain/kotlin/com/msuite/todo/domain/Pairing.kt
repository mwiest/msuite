package com.msuite.todo.domain

import com.msuite.core.crypto.BackendType
import com.msuite.core.crypto.KEY_GEN_V1
import com.msuite.core.crypto.PairingPayload
import com.msuite.core.crypto.RecoveryWords

/** What a scanned code or a typed recovery key did, for the screen's message (step 7). */
sealed interface JoinResult {
    /** The group's root is this device's from now on; [backend] is the group's transport. */
    data class Joined(val backend: BackendType?) : JoinResult

    /** This device already holds that root — the QR was its own. */
    data object AlreadyThisGroup : JoinResult

    /** A root generation this build doesn't hold (decision 0008). */
    data object UnsupportedKeyGen : JoinResult

    /** Not a pairing payload, or words that don't encode a root (records 0072, 0073). */
    data object Unreadable : JoinResult
}

/** What a read code would do, before the user confirms that its data is shared (decision 0033). */
sealed interface JoinPreview {
    /** A different group this build can join; the UI asks for confirmation next. */
    data object Ready : JoinPreview

    /** This device already holds that root — the QR was its own. */
    data object AlreadyThisGroup : JoinPreview

    /** A root generation this build doesn't hold (decision 0008). */
    data object UnsupportedKeyGen : JoinPreview

    /** Not a pairing payload (record 0072). */
    data object Unreadable : JoinPreview
}

/**
 * Pairing a device by QR (decisions 0008, 0033, 0072) and restoring from the recovery words
 * (decision 0011, record 0073). Both paths end in [StoredIdentity.adopt], so there is one place
 * that takes a group root. Neither path touches the stored data: a joining device keeps its lists
 * and tasks and shares them (0033) — the app only has to say so before it joins.
 */
class PairingActions(private val identity: StoredIdentity) {
    /** The bytes this device shows as its QR, or `null` when it holds no root yet. */
    suspend fun code(): ByteArray? =
        identity.root(KEY_GEN_V1)?.let { PairingPayload.build(KEY_GEN_V1, it, BackendType.Manual) }

    /**
     * Reads a scanned [code] without adopting it, so the UI can refuse a bad code before it asks
     * the user to share the device's data (0033).
     */
    suspend fun preview(code: ByteArray): JoinPreview {
        val payload = PairingPayload.parse(code) ?: return JoinPreview.Unreadable
        return when {
            payload.keyGen != KEY_GEN_V1 -> JoinPreview.UnsupportedKeyGen
            identity.root(KEY_GEN_V1)?.contentEquals(payload.root) == true -> JoinPreview.AlreadyThisGroup
            else -> JoinPreview.Ready
        }
    }

    /** Adopts the group root a scanned [code] carries; the caller confirms the share first (0033). */
    suspend fun join(code: ByteArray): JoinResult {
        val payload = PairingPayload.parse(code) ?: return JoinResult.Unreadable
        return if (payload.keyGen == KEY_GEN_V1) adopt(payload.root, payload.backend) else JoinResult.UnsupportedKeyGen
    }

    /** Adopts the root [words] encode: the camera-less path, with no transport to learn (0073). */
    suspend fun restore(words: List<String>): JoinResult =
        RecoveryWords.decode(words)?.let { adopt(it, null) } ?: JoinResult.Unreadable

    private fun adopt(root: ByteArray, backend: BackendType?): JoinResult =
        if (root.contentEquals(identity.root(KEY_GEN_V1))) {
            JoinResult.AlreadyThisGroup
        } else {
            identity.adopt(root)
            JoinResult.Joined(backend)
        }
}
