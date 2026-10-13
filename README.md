# Msuite

A coherent set of **FOSS, local-first** mobile apps — small "handy helper" utilities that share design, principles, and an optional serverless sync layer. Use one, and the others feel like home.

## Philosophy

- **Local-first** — your data lives on your device and works fully offline.
- **No registration** — no accounts, ever. Your identity is a key on your device.
- **No mandatory backend** — sync is optional, end-to-end encrypted, and uses a backend *you* choose (a synced folder, your own server, a cheap bucket…). We host nothing.
- **No telemetry** — no analytics, no crash-reporting SDKs.
- **FOSS & F-Droid-friendly** — open source, no proprietary Google dependencies.
- **Good, simple UX** — Material 3, opinionated defaults, no clutter.

## Planned apps

| App | What it does | Status |
|---|---|---|
| ToDo | Tasks and lists | In progress (first app) |
| Notes | Text/markdown notes, tags, search | Planned |
| Contacts | People, fields, groups | Planned |
| Calendar | Events, recurrence, reminders | Planned |
| More | Habits, expenses, secrets vault, bookmarks, journal… | Ideas |

## How sync works

*Being built, ToDo first.* Sync is optional. Without it, each app is a complete offline app.

```text
 Phone                             Backend you pick                Tablet
+------------------------+        +--------------------+          +--------------------------+
| Your edits             |        |                    | download | Fetch unseen batches,    |
|     |                  |        | Locked batches     |--------->| decrypt                  |
|     v                  |        |                    |          |     |                    |
| Local database         |        | write-once,        |          |     v                    |
|     |                  |        | unique names,      |          | Merge: newest edit       |
|     v                  |        | never overwritten  |          | per field wins           |
| Pack new changes       | upload |                    |          |     |                    |
| into a batch,          |------->|                    |          |     v                    |
| encrypt                |        |                    |          | Local database           |
+------------------------+        +--------------------+          +--------------------------+
```

The same happens the other way round, and with any number of devices.

**The pieces:**

- **One shared key, no accounts.** Your devices share one secret key, used by all msuite apps. You
  add a device by scanning a QR code, and back up the key as a list of words. The backend sees only
  encrypted batches, and can't alter, rename or swap them without the apps noticing.
- **Batches.** A device packs everything that changed since its last upload into a batch. It
  remembers which batches it has already merged, and downloads only the new ones.
- **Every field is stamped.** Each value (a task's title, its due date, whether it's done) is
  stored with a timestamp and the ID of the device that wrote it.
- **HLC (Hybrid Logical Clock).** The stamp is a clock reading plus a small counter, so it never
  goes backwards. A device that sees a newer stamp from another device moves its own clock past
  it, so a later edit always gets a later stamp. Ties go to the higher device ID, so every device
  picks the same winner. Changes stamped more than a day in the future (a wrongly set clock) are
  held back until the clocks agree.
- **LWW (Last Writer Wins), per field.** When two versions meet, each field keeps the value with
  the newest stamp. Rename a task on your phone and tick it off on your tablet: both changes
  survive. Rename it on both: the later rename wins, the other is lost, which is fine for to-dos.
- **Order doesn't matter.** The merge looks only at the stamps, so devices can exchange batches
  in any order, twice, or after weeks offline, and still end up with the same data.
- **Deleting is just another field.** A delete sets a "deleted" flag with a stamp, so an older
  edit arriving later can't bring the item back.
- **Manual sorting.** An item's place in a list is a sort key that fits between its neighbours, so
  moving an item changes only that item.

**Backends are dumb file stores.** A backend only stores, lists and returns files; it never
changes or merges them. All merging happens inside the apps, so very different backends work the
same way:

| Backend | Good for |
|---|---|
| Manual file export/import | No setup at all, a one-off transfer |
| Synced folder (Syncthing, Nextcloud, Dropbox) | Using a sync tool you already run |
| WebDAV (e.g. Nextcloud) | A server you already have |
| Object storage (S3, Cloudflare R2, Backblaze B2, MinIO) | Cheap, always-on storage |
| msuite relay | A tiny server you host yourself |

Every batch gets a unique name and is never overwritten, so two devices uploading at the same time
can't clobber each other, even on a backend without locking. Details:
[`docs/architecture.md`](docs/architecture.md).

## Status

Early development; a proof of concept. Not yet released.

## License

**GPL-3.0-or-later** for everything, including the sync relay server.

---

What msuite is: [`docs/product.md`](docs/product.md) · Architecture: [`docs/architecture.md`](docs/architecture.md) · Roadmap: [`docs/roadmap.md`](docs/roadmap.md)
