# Roadmap

Upcoming work, one line each. Work with a plan links to it; the rest gets a plan when it starts.

## In progress

- v1, a ToDo app that validates the sync layer: [`plans/v1-todo.md`](plans/v1-todo.md)

## Planned

After v1, roughly in this order:

- App #2, Notes, with federation: a new app imports the identity and sync settings from a sibling
  (decision 0001).
- A per-field merge (CRDT or 3-way) for fields where LWW loss is unacceptable, e.g. a notes body
  (decision 0005).
- More plugs: synced folder (SAF), WebDAV, object storage (S3, R2, B2, MinIO).
- Snapshots with pruning, `delete` and a plug capability descriptor (decisions 0002, 0003).
- Contacts, then Calendar.
- First F-Droid release of ToDo, with a privacy policy, release docs and store metadata.
- Web app (`web/README.md`, decision 0012).

## Known problems

- A machine without any JDK 21 can't build: the daemon is pinned to 21 and no toolchain download
  is configured. Gradle 8.13 itself can't run on JDK 25.
- CI has not run yet (nothing pushed since it was added).

## Ideas

Deferred, each addable later without a format change unless noted:

- Tell the user when a device's batches are deferred for a clock far ahead (decision 0040).
- Sync through two plugs at once, which needs per-plug change tracking (decision 0036).
- Undelete for lists; their hidden tasks would reappear (decision 0032).
- Key rotation after device loss, using the `keyGen` id (decision 0008).
- Metadata mitigations: size buckets, pseudonymous rotating `deviceId` (decision 0009; per-app
  namespaces are already in, decision 0029).
- Passphrase-based recovery with Argon2id (decision 0011).
- SQLCipher at rest, opt-in (decision 0011; needs a native `.so`).
- Reminders: `AlarmManager` / `WorkManager`, no FCM (decision 0014).
- Fractional-index key rebalancing (decision 0004).
- Removing tombstones for good (decision 0002).
- GraalVM native image of the relay (decision 0017).
- More UI languages beyond English and German (decision 0027).
- Further apps: habit tracker, expense tracker, secrets vault, bookmarks / read-later, recipes,
  document scanner, journal. Common thread: a small data model, privacy as a real selling point,
  and a natural pairing with another app.
