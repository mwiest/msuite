package com.msuite.todo.domain

/**
 * Picking a synced folder: the platform half of the folder plug (decision 0042). The shell
 * implements it with SAF's folder picker on Android; the UI calls [pick] to show the picker.
 * Returns the folder's URI string, or `null` if the user cancelled.
 */
interface FolderPick {
    suspend fun pick(): String?
}
