# msuite: product

What msuite is and what its apps do today. When behaviour changes, this file changes in the same
commit. What is intended but not built is in `roadmap.md` and the plans.

Checked against the code on 2026-11-10: **the ToDo app works offline on one device, syncs live
with paired devices through a folder the user already syncs (Dropbox, Syncthing, Nextcloud), a
self-hosted relay or an S3-compatible bucket, exports
an encrypted whole-state snapshot file, pairs a second device by QR, and shows or restores the group
key as 24 recovery words.** On first
start it
creates "My Tasks" (in the device language). The lists are tabs at the top, and the app reopens on
the list last shown. The selected list shows its open tasks, newest first, then its done tasks,
grayed out and struck through; each has a checkbox, a star, and its notes' first line and due date
below the title. The add button opens a sheet to type a new task's title. Tapping a task opens its
editor in a sheet: title, notes, star, a due date picked from a calendar (display only, no reminder),
mark done or not done, and delete in the overflow menu. Title and notes are saved when you leave the
field or close the sheet. A long press drags an open task to a new place. The "+" tab creates a list;
the top bar's menu renames the selected list, moves it left or right, or deletes it after confirming
how many tasks go with it ("My Tasks" can't be deleted). The gear opens the settings: the theme
(system default, light or dark), a Sync method chooser (off, a synced folder, a self-hosted relay,
or S3 storage) that picks the folder, takes the relay's https address, or takes a bucket's endpoint,
region, bucket name, optional folder and access key pair (the secret is never shown again: the form
says it is stored on the device and offers Change to replace it; Connect first checks that the relay or
bucket answers and, if not, says why and keeps the current method), shows the group ID an operator's allowlist needs, and reports the last sync
or why it is failing (folder, relay or bucket unreachable, key refused, registration refused,
namespace full, access key rejected or not allowed, no such bucket, wrong region, device clock
off, or a bucket secret this device no longer has), with sync on demand, a Sync
section that exports the device's whole state to an
encrypted file or merges one in (the file is ciphertext under the group's root, so it imports on
another device once the two are paired), a Pairing section that shows this device's QR code or scans
another's with the camera (joining asks first and says the device's tasks will be shared), a
Recovery section that shows this device's 24 words, copies them as a sensitive clipboard entry that
clears after 60 seconds, or restores the key from typed words (after the same confirmation), and an
About section with the version, author, licence (GPL v3 or later), a link to the source code and the
open-source libraries used. Everything is kept on the device; Android backup is off. Its intended
features are in [`plans/v1-todo.md`](plans/v1-todo.md).

## What msuite is

A coherent set of FOSS, local-first mobile apps: small "handy helper" utilities that share design,
principles, and an optional serverless sync layer, so a user of one feels at home in the others.

It is a **proof of concept, not a product** (decision 0013). The thesis: a solo developer can build
a coherent, privacy-first FOSS suite with good UX and a shared, serverless, end-to-end-encrypted
sync layer. Success is a working demonstration of the architecture and UX, not adoption.

## Principles

| Principle | Meaning |
|---|---|
| FOSS | Open source (GPLv3, decision 0023), F-Droid-friendly. |
| Local-first | Data lives on the device; everything works offline. |
| No registration | No accounts. Identity is a shared symmetric secret (decision 0008). |
| No mandatory backend | The developer hosts nothing; sync is optional and goes through a backend the user picks. |
| No telemetry | No analytics, no crash-reporting SDKs (decision 0024). |
| Good UX | Simple, low-friction, opinionated defaults. Material 3 across all apps. |
| No proprietary Google dependencies | No GMS, FCM, ML Kit or Maps, so F-Droid publishing stays clean. |

## Coherence across apps

- Shared design language, UX patterns, and one identity and sync engine.
- Each app is useful on its own; the suite is additive.
- Each app keeps its own data (decision 0001). Apps link to each other by URI
  (`msuite://notes/<id>`) where natural, e.g. task to note, contact to calendar.
- Pair once per device: a newly installed app can import the identity and sync settings from an
  installed sibling, after the user confirms.

## Sync, as the user sees it

Partly built. Settings has Export and Import: Export writes the device's whole state to one
encrypted file, Import merges a chosen file in. The file is AES-256-GCM ciphertext under the group's
root secret, so a backend or anyone who finds it sees only opaque bytes; it imports on any device
that holds the group's root, which pairing by QR gives it. Live sync runs through one backend at a
time, chosen in Settings: a synced folder, where each device writes its edits 10 seconds after the
last one as encrypted files into the picked folder and reads the others' every 10 seconds while
the app is open, or a self-hosted relay, where each device stores the same encrypted objects over
HTTP and authenticates with a token derived from the root, or an S3-compatible bucket (Cloudflare
R2, Backblaze B2, AWS, MinIO), where each device stores the same encrypted objects and signs its
requests with the bucket's access key. The bucket's secret key is kept wrapped under the Android
KeyStore, like the group's root, never in the plain settings, and is deleted when the device
switches to another backend. A folder, relay or bucket that can't be reached never closes the app;
Settings says so and the edits wait on the device until it can. A relay speaks plain HTTP behind a
TLS reverse proxy; the app only accepts https addresses for a relay or bucket (a debug build also
accepts http for a LAN or emulator). A device periodically folds its own edits into one
snapshot and removes what the snapshot covers, so a backend stays bounded; if a folder or
namespace is emptied, a device that has synced before says it looks empty and, once the user
confirms that it is, refills it. Architecture in
`architecture.md`.

- Sync is optional. The user picks a backend: none (export/import a file), a synced folder
  (Syncthing, Nextcloud, Dropbox), WebDAV, an S3-compatible bucket, or the self-hostable msuite
  relay. All of them see only ciphertext.
- A new device joins by scanning one QR code from a paired device. If it already holds lists and
  tasks, they are kept and shared with the other devices. A code from a relay group selects that
  relay on the joining device; a code from an S3 group selects that bucket, access key included, so
  whoever sees the code can also use the bucket; a folder is picked per device. Scanning a code of
  the device's own group that names another relay or bucket offers to switch to it. A recovery key
  (words, QR or file) is the fallback when no device is left. There is no password reset.
- When two devices edit the same field while apart, the later edit wins and the other is lost
  (decision 0005).

## App roster

First four, in rough order of complexity: **ToDo, Notes, Contacts, Calendar** (Calendar is
hardest: recurrence, CalDAV-like). Further ideas are in `roadmap.md`.

## Non-goals

- Replicating heavyweight apps (Docs, Photos).
- Running or maintaining any backend or hosted service for users.
- Accounts, logins, proprietary SDKs.
- Monetization. Solo hobby project, unfunded; donations optional, never required.
- Adoption or user growth as a success metric. Features that prove nothing about the shared layer
  (e.g. reminders, decision 0014) wait.
