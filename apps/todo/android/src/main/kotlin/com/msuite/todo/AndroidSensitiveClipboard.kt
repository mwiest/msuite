package com.msuite.todo

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import com.msuite.todo.domain.SensitiveClipboard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The recovery key's clip (decision 0041). On Android 13+ it is marked sensitive, so the system
 * preview shows dots and well-behaved keyboards keep it out of their history. The app clears the
 * clip 60 s later unless something else was copied; the timer belongs to the application's scope,
 * so a rotation does not leave the words on the clipboard.
 */
class AndroidSensitiveClipboard(context: Context, private val scope: CoroutineScope) : SensitiveClipboard {
    private val clipboard = context.getSystemService(ClipboardManager::class.java)
    private var clearJob: Job? = null

    override fun copy(text: String) {
        clipboard.setPrimaryClip(clip(text))
        clearJob?.cancel()
        clearJob = scope.launch {
            delay(CLEAR_AFTER_MS)
            // Best effort: Android 10+ refuses a background read, so an unreadable clip is cleared
            // too — leaving a root on the clipboard is worse than clearing a newer copy.
            val current = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
            if (current == null || current == text) clipboard.clearPrimaryClip()
        }
    }

    private fun clip(text: String): ClipData {
        val data = ClipData.newPlainText(LABEL, text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            data.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        return data
    }

    private companion object {
        const val LABEL = "msuite recovery key"
        const val CLEAR_AFTER_MS = 60_000L
    }
}
