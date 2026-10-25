# 0075. SAF file store uses local temp directory for atomic writes

**Date**: 2026-10-25

## Context

The folder plug uses an atomic write pattern: write to `.tmp_*` file, then rename to final name. This ensures other devices only see complete files.

For local files, this works fine. But cloud document providers like Dropbox have issues:
- Dropbox doesn't support `renameDocument()` (throws `FileNotFoundException: invalid path`)
- Dropbox has eventual consistency: files written aren't immediately readable
- Dropbox creates duplicate files with "(1)", "(2)" suffixes when it detects naming conflicts during sync

## Decision

The `SafFileStore` (Android SAF implementation) uses a **local temp directory** for `.tmp_*` files:

1. When writing a `.tmp_*` file, write to `context.cacheDir/sync_temp/<path>` instead of cloud
2. When renaming from `.tmp_*` to final name:
   - Read bytes from local temp file
   - Write directly to cloud at final path
   - Delete local temp file
3. After writing to cloud, scan for and delete any duplicates (files matching `<name> (N)` pattern)

Non-temp files continue to write directly to cloud storage.

## Consequences

**Positive**:
- Atomic semantics preserved: final file in cloud is either fully written or doesn't exist
- Works around Dropbox's eventual consistency (no read-after-write on temp files)
- Automatically cleans up any duplicates Dropbox creates during sync
- No `.tmp_*` files pollute the cloud folder

**Negative**:
- Requires local cache space for temp files (cleaned up after rename)
- Dropbox-specific workaround in a generic SAF implementation
- Adds complexity to the write path

**Neutral**:
- Other SAF providers (Nextcloud, Syncthing) work fine with or without this workaround
- The duplicate cleanup is defensive and handles edge cases

## Implementation

The `SafFileStore` detects temp files by checking if the filename starts with `.tmp_`:

```kotlin
private fun isTempPath(path: String): Boolean {
    val name = path.substringAfterLast('/')
    return name.startsWith(".tmp_")
}

private fun localTempFile(path: String): File {
    val file = File(context.cacheDir, "sync_temp")
    file.mkdirs()
    return File(file, path)
}
```

All file operations (`read`, `write`, `delete`, `exists`) check `isTempPath()` and route to local or cloud accordingly.

The `rename()` operation handles three cases:
1. Local → Cloud (temp to final): read local, write cloud, delete local
2. Cloud → Cloud: try native rename, fallback to copy+delete
3. Cloud → Local: not used in practice

## Testing

Tested on two Android devices (OnePlus Nord, Motorola Edge 70 Pro) syncing via Dropbox:
- Create todo on device A → appears on device B within ~15 seconds
- No duplicate files in Dropbox
- Logs show successful local temp write, rename to cloud, and duplicate cleanup
