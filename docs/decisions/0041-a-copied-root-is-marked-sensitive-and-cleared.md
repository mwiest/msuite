# 0041: A copied root is marked sensitive and cleared after 60 seconds

Date: 2026-10-11. Status: accepted.

## Context

The recovery key can be copied to the clipboard so the user can paste it into a password manager
(0011). While it is there, the clipboard holds the root: full access to all data, for good (0008).
On Android, keyboards and clipboard managers keep a history (some sync it to their cloud), Android
13+ shows a preview of copied text on screen, and any app the user pastes into sees it.

## Decision

- The clip is marked sensitive (`ClipDescription.EXTRA_IS_SENSITIVE`, honoured from Android 13):
  the system preview shows dots, and well-behaved keyboards keep it out of their history.
- The app clears the clipboard 60 seconds after copying, unless something else has been copied
  since. The copy confirmation says so.

## Rejected

- No clipboard at all (QR or file export only): safer, but the password-manager path of 0011 would
  turn into manual typing.
- Copying without protection: the root ends up in keyboard histories and screenshots.

## Consequences

About 10 lines, no dependency; built with the recovery key in v1 step 7. It doesn't protect
against a malicious keyboard, which sees everything anyway. A user who takes longer than a minute
to paste has to copy again.
