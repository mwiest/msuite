package com.msuite.todo

import com.msuite.core.sync.FileStore
import com.msuite.todo.domain.FolderPick
import com.msuite.todo.domain.QrCodes
import com.msuite.todo.domain.SensitiveClipboard
import com.msuite.todo.domain.SnapshotFiles

/**
 * What only a platform shell can do for the UI: pick a file for the manual plug, pick a folder for
 * the folder plug, draw and scan a pairing code, copy the recovery key (decisions 0012, 0041, 0042).
 * `TodoContainer` holds the domain side the same way, and the shell (`apps/todo/android`) builds
 * this one.
 */
class ShellServices(
    val files: SnapshotFiles,
    val folder: FolderPick,
    val qr: QrCodes,
    val clipboard: SensitiveClipboard,
    val fileStoreFactory: (String) -> FileStore,
)
