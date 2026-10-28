# Roadmap

Upcoming work, one line each. Work with a plan links to it; the rest gets a plan when it starts.

## In progress

- v1, a ToDo app that validates the sync layer: [`plans/v1-todo.md`](plans/v1-todo.md)
  - step 8b, the Dropbox device check: [`plans/v1-step8b-dropbox-conflicts.md`](plans/v1-step8b-dropbox-conflicts.md)

## Planned

After v1, roughly in this order:

- App #2, Notes, with federation: a new app imports the identity and sync settings from a sibling
  (decision 0001).
- A per-field merge (CRDT or 3-way) for fields where LWW loss is unacceptable, e.g. a notes body
  (decision 0005).
- More plugs: WebDAV, object storage (S3, R2, B2, MinIO).
- Contacts, then Calendar.
- First F-Droid release of ToDo, with a privacy policy, release docs and store metadata.
- Web app (`web/README.md`, decision 0012).

## Known problems

- A machine without any JDK 21 can't build: the daemon is pinned to 21 (detekt 1.23 fails on
  JDK 25) and no toolchain download is configured. Drop the pin once detekt 2 is stable.
- Android-only Compose modules log "commonTest was configured but not added to any compilation".
- CI runs failed lately; investigate first thing (step 1 of [`plans/v1-step8b-dropbox-conflicts.md`](plans/v1-step8b-dropbox-conflicts.md#next-session)). `allTests` was red from `ea2d558` to `891630f`.
- The APK holds `libandroidx.graphics.path.so` (AndroidX, Apache-2), pulled in by Compose UI
  through `androidx.graphics:graphics-path`. It clashes with the letter of "no native `.so`",
  whose reason is F-Droid and SQLCipher; decide whether the rule means our own native code only.

## Ideas

Deferred, each addable later without a format change unless noted:

- Tell the user when a device's batches are deferred for a clock far ahead (decision 0040).
- Sync through two plugs at once, which needs per-plug change tracking (decision 0036).
- Undelete for lists; their hidden tasks would reappear (decision 0032).
- Key rotation after device loss, using the `keyGen` id (decision 0008).
- Metadata mitigations: size buckets, opaque per-app namespaces, pseudonymous rotating `deviceId`
  (decisions 0009, 0029).
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
