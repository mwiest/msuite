# 0078: Folder sync cleans up after the sync client and tolerates the echo

Date: 2026-10-30. Status: accepted.

## Context

Two devices syncing through Dropbox over SAF (v1 step 8 device check, 2026-10-27 to 2026-10-30)
showed what a sync client adds to a folder on its own:

- **`key (N)` copies, 0 bytes:** made on the *writer*, seconds after a single delete → create →
  write, about one write in three on one device. Most likely the empty `createFile` revision and
  the content revision upload as two conflicting revisions. SAF offers no create-with-content.
- **`key_<digits>` copies with data:** made when a device put a key the server already held but
  its view didn't show (two devices publishing the same key, which the old device id bug caused).
- **A twin group folder** `{groupId}_<digits>`: two devices creating `todo/{groupId}` at once each
  got one, Dropbox renamed the loser's, and the batches written there were never listed again.

Separately, a delta is every cell above the plug's watermark (decision 0036), so a peer's cells
ingested with a higher HLC are published again by the receiver: the *echo*. Since keys name their
writer it is a duplicate object, never a clash.

## Decision

- A pull deletes `key (N)` and `key_<digits>` while `key` itself is listed; copies of a key that
  isn't listed are kept, and names that don't parse as keys are never fetched.
- The folder plug adopts a twin of the namespace folder on each listing: it moves the twin's
  files in, create-only, and deletes the twin.
- The echo stays. Ingest is idempotent, so it costs uploads, not correctness.

## Rejected

- Deleting copies right after the write (`SafFileStore`, until 2026-10-28): the copy appears
  seconds later, so the cleanup never saw it.
- Preventing the `(1)` copies through SAF: there is no single call that creates a document with
  content, and Dropbox's provider claims `rename` but throws on it (decision 0075). Still wanted;
  it needs another route, see the roadmap.
- Serialising folder creation (an existence check plus a settle delay): the creating device's view
  is the stale one, so no delay is long enough; adopting the twin works whatever the timing.
- Filtering the echo now: needs the origin plug per cell, which the relay and multi-plug work will
  need anyway (decision 0036, "Rejected").

## Consequences

The folder stays clean without user action, and a batch written into a twin is recovered on the
next listing; decision 0077 resends what was lost any other way. The plug deletes files it didn't
write, but only exact copies of a key that is present, or a twin's files after copying them. In a
two-device group about half the published objects are echoes; revisit with the relay.
