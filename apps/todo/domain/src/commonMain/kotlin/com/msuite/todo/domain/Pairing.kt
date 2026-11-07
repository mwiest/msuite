package com.msuite.todo.domain

import com.msuite.core.crypto.BackendType
import com.msuite.core.crypto.KEY_GEN_V1
import com.msuite.core.crypto.PairingPayload
import com.msuite.core.crypto.RecoveryWords

/** The backend descriptor this device puts in its QR (record 0072): type plus its section. */
data class PairingBackend(val type: BackendType, val section: ByteArray = ByteArray(0))

/** What a scanned code or a typed recovery key did, for the screen's message (step 7). */
sealed interface JoinResult {
    /** The group's root is this device's from now on; [backend] is the group's transport. */
    data class Joined(
        val backend: BackendType?,
        val backendSection: ByteArray = ByteArray(0),
    ) : JoinResult

    /**
     * The words adopted the group's root (record 0073). They carry no backend, so the device's
     * transport is whatever it already had.
     */
    data object Restored : JoinResult

    /** This device was already in the group and now uses the code's [backend] (record 0080). */
    data class SwitchedBackend(val backend: BackendType) : JoinResult

    /** This device already holds that root — the QR or words were its own. */
    data object AlreadyThisGroup : JoinResult

    /** A root generation this build doesn't hold (decision 0008). */
    data object UnsupportedKeyGen : JoinResult

    /** Not a pairing payload, or words that don't encode a root (records 0072, 0073). */
    data object Unreadable : JoinResult
}

/** What a read code or typed words would do, before the user confirms that data is shared (0033). */
sealed interface JoinPreview {
    /** A different group this build can join; the UI asks for confirmation next. */
    data object Ready : JoinPreview

    /** This device already holds that root — the QR or words were its own. */
    data object AlreadyThisGroup : JoinPreview

    /** A code of this device's own group naming another relay; the UI offers to switch to it. */
    data class SwitchBackend(val relayUrl: String) : JoinPreview

    /** A root generation this build doesn't hold (decision 0008). */
    data object UnsupportedKeyGen : JoinPreview

    /** Not a pairing payload, or words that don't encode a root (records 0072, 0073). */
    data object Unreadable : JoinPreview
}

/**
 * Pairing a device by QR (decisions 0008, 0033, 0072) and restoring from the recovery words
 * (decision 0011, record 0073). Both paths end in [StoredIdentity.adopt], so there is one place
 * that takes a group root. Neither path touches the stored data: a joining device keeps its lists
 * and tasks and shares them (0033) — the app only has to say so before it joins.
 */
class PairingActions(
    private val identity: StoredIdentity,
    private val backend: () -> PairingBackend,
    private val onJoined: (BackendType?, ByteArray) -> Unit = { _, _ -> },
    private val onSwitch: (BackendType, ByteArray) -> Boolean = { _, _ -> false },
) {

    /** The bytes this device shows as its QR, or `null` when it holds no root yet. */
    suspend fun code(): ByteArray? = identity.root(KEY_GEN_V1)?.let {
        val descriptor = backend()
        PairingPayload.build(KEY_GEN_V1, it, descriptor.type, descriptor.section)
    }

    /** The 24 recovery words for this device's root, or `null` when it holds none (record 0073). */
    suspend fun words(): List<String>? = identity.root(KEY_GEN_V1)?.let { RecoveryWords.encode(it) }

    /**
     * Reads a scanned [code] without adopting it, so the UI can refuse a bad code before it asks
     * the user to share the device's data (0033).
     */
    suspend fun preview(code: ByteArray): JoinPreview {
        val payload = PairingPayload.parse(code) ?: return JoinPreview.Unreadable
        val relayUrl = switchableRelay(payload)
        return when {
            payload.keyGen != KEY_GEN_V1 -> JoinPreview.UnsupportedKeyGen
            relayUrl != null -> JoinPreview.SwitchBackend(relayUrl)
            else -> previewRoot(payload.root)
        }
    }

    /**
     * Moves this device to the relay a code of its own group names. Only a relay is offered: a
     * folder or manual code carries no setup, and would only turn this device's sync off.
     */
    suspend fun switchBackend(code: ByteArray): JoinResult {
        val payload = PairingPayload.parse(code)?.takeIf { it.keyGen == KEY_GEN_V1 && switchableRelay(it) != null }
        return when {
            payload == null -> JoinResult.Unreadable
            onSwitch(BackendType.Relay, payload.backendSection) -> JoinResult.SwitchedBackend(BackendType.Relay)
            else -> JoinResult.AlreadyThisGroup
        }
    }

    /** The relay URL of a code of this device's own group, when it isn't the one in use. */
    private fun switchableRelay(payload: PairingPayload): String? {
        if (!payload.root.contentEquals(identity.root(KEY_GEN_V1)) || payload.backend != BackendType.Relay) return null
        val current = backend()
        val same = current.type == BackendType.Relay && current.section.contentEquals(payload.backendSection)
        return if (same) null else PairingPayload.relayUrl(payload.backendSection)
    }

    /** Reads typed [words] without adopting them: the camera-less confirm step (0033, 0073). */
    suspend fun preview(words: List<String>): JoinPreview =
        RecoveryWords.decode(words)?.let(::previewRoot) ?: JoinPreview.Unreadable

    /** Adopts the group root a scanned [code] carries; the caller confirms the share first (0033). */
    suspend fun join(code: ByteArray): JoinResult {
        val payload = PairingPayload.parse(code) ?: return JoinResult.Unreadable
        return if (payload.keyGen == KEY_GEN_V1) {
            adopt(payload.root) {
                onJoined(payload.backend, payload.backendSection)
                JoinResult.Joined(payload.backend, payload.backendSection)
            }
        } else {
            JoinResult.UnsupportedKeyGen
        }
    }

    /** Adopts the root [words] encode: the camera-less path, with no transport to learn (0073). */
    suspend fun restore(words: List<String>): JoinResult =
        RecoveryWords.decode(words)?.let { adopt(it) { JoinResult.Restored } } ?: JoinResult.Unreadable

    private fun previewRoot(root: ByteArray): JoinPreview =
        if (root.contentEquals(identity.root(KEY_GEN_V1))) JoinPreview.AlreadyThisGroup else JoinPreview.Ready

    private fun adopt(root: ByteArray, joined: () -> JoinResult): JoinResult =
        if (root.contentEquals(identity.root(KEY_GEN_V1))) {
            JoinResult.AlreadyThisGroup
        } else {
            identity.adopt(root)
            joined()
        }
}
