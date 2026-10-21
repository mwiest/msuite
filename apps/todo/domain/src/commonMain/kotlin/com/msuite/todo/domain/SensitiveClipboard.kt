package com.msuite.todo.domain

/**
 * The recovery key's clipboard (decision 0041). It stays behind this seam (AGENTS.md): the Android
 * shell marks the clip sensitive so the system preview shows dots, and clears it after 60 seconds.
 */
interface SensitiveClipboard {
    /** Copies [text] as a sensitive clip that the implementation clears after 60 seconds. */
    fun copy(text: String)
}
