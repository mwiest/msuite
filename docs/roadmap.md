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

- With Android Studio's bundled JDK 25 as `JAVA_HOME`, the Gradle 8.13 build fails with only
  "25.0.3" as the message. Use JDK 21 (decision 0022).

## Ideas

Deferred, each addable later without a format change unless noted:

- Max-drift clamp on incoming HLCs, once a bad clock shows up (decision 0005).
- Key rotation after device loss, using the `keyGen` id (decision 0008).
- Metadata mitigations: size buckets, per-app credentials, pseudonymous rotating `deviceId`
  (decision 0009).
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
