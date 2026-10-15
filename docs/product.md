# msuite: product

What msuite is and what its apps do today. When behaviour changes, this file changes in the same
commit. What is intended but not built is in `roadmap.md` and the plans.

Checked against the code on 2026-10-15: **the ToDo app stores its data on the device but has no
screens yet.** On first start it creates "My Tasks" (in the device language) and shows the lists
and task titles it holds; Android backup is off. Its intended features are in [`plans/v1-todo.md`](plans/v1-todo.md).

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

Planned; nothing is built yet. Architecture in `architecture.md`.

- Sync is optional. The user picks a backend: none (export/import a file), a synced folder
  (Syncthing, Nextcloud, Dropbox), WebDAV, an S3-compatible bucket, or the self-hostable msuite
  relay. All of them see only ciphertext.
- A new device joins by scanning one QR code from a paired device. If it already holds lists and
  tasks, they are kept and shared with the other devices. A recovery key
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
