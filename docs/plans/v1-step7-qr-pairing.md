# Plan: v1 step 7, QR pairing and the recovery key

Status: **slices 1 to 4 done; slice 5 (the scan) is built and awaits its device check; slices 6 and 7
remain.** Started 2026-10-18. A slice of [`v1-todo.md`](v1-todo.md) step 7.

## Goal

Two devices that never met share one root after one QR scan, and a user with no device left
restores the root from words. That makes the step 6 snapshot file portable for the first time —
pair, then export/import between the two devices — and proves key distribution without accounts
(decisions 0008, 0033). The joining device keeps and shares its data; the words are the camera-less
fallback, copied as a sensitive clip that clears after 60 s (decisions 0011, 0041).

## Decisions

- The QR carries identity **and the group's backend descriptor** (decides `v1-todo.md`'s open
  question; record 0072 with slice 1): a version byte, `keyGen`, the 32-byte root, the backend
  type, the type's section, and a checksum — the first 4 bytes of SHA-256 over everything before
  it (0008's "root plus a checksum"). Identity sits at fixed offsets, so a reader that doesn't
  know the backend type still joins and says so.
- v1 writes backend type "manual" with an empty section: nothing portable exists to carry. The
  folder type (step 8) carries nothing either — a SAF grant is device-bound — but the joining
  device then offers its folder chooser directly; backends with portable config (the relay, step 9)
  carry it in their section. A section's layout is fixed per version and type; unknown bytes are
  skipped, not failed.
- The payload is binary, built and parsed in `core/crypto` beside the envelope: suite-level, so a
  sibling app can scan the same QR (decision 0001). 42 bytes today, one small QR.
- The QR is shown on demand and stored nowhere else; whoever captures it has the root (0008's
  accepted gap).
- Scanning is zxing-android-embedded (journeyapps, Apache-2) behind a shell seam — `ScanContract`
  plus `registerForActivityResult`, the SafSnapshotFiles pattern; display is ZXing core's matrix
  drawn on a Canvas. zxing-cpp is rejected: a native `.so` (0011). Record 0074. Both are
  Android-only platform halves (0012); the web app swaps them later.
- The joining device adopts the root in the app layer: `StoredIdentity` learns to replace its
  root — `RootStore.save` already overwrites; the cached secret must swap too — while `core`'s
  `IdentityProvider` stays read-only. The `deviceId` and all data are kept (0033); the old root's
  files become unreadable, like any foreign root. The confirm step says the device's data will be
  shared with the group (0033).
- Pairing transfers the key only; data still travels by the plug — the manual file now, the
  folder at step 8. Scanning your own QR is a no-op ("already this group").
- Recovery words are BIP39 over the root: 24 words, checksum the first 8 bits of SHA-256, the
  English wordlist in-repo (~13 KB; BIP39 is frozen), official vectors in the tests. Record 0073.
  Words are a canonical encoding, not localizable prose, so English-only leaves 0027 untouched.
- Recovery in v1 is the words: shown, copied, and re-entered on a fresh install — all three end
  in the same adopt call as pairing. The QR and file forms, and the durability nudge, come with a
  backend (0011), i.e. step 8.
- The join UI states the group's transport in words ("syncs by file transfer" today); a type's
  behaviour, like the folder chooser, lands with the plug that has one.

## Slices

Each slice is one commit, builds, and leaves the build green.

1. [x] **The pairing payload** in `core/crypto`: build and parse version, `keyGen`, root, backend
   type and section, checksum. jvmTest: round trip, every single-bit flip in the covered bytes is
   rejected, an unknown backend type still yields the identity. Record 0072. Landed: `71ce6ee`.
2. [x] **Recovery words** in `core/crypto`: BIP39 encode and decode over the 32-byte root, the
   in-repo wordlist. jvmTest: the official BIP39 vectors, a wrong word rejected, a bad checksum
   rejected. Record 0073. Landed: `978f4ff`.
3. [x] **Adopting a root** in `apps/todo/domain`: `PairingActions` — parse, confirm, adopt
   (`StoredIdentity` swaps and saves its secret; the `deviceId` and data stay); scanning your own
   QR reports "already this group"; restoring from words reuses the same path. A cross-replica
   test in `core/sync` jvmTest: two replicas, two roots; B adopts A's root and A's export then
   imports on B — both converge holding both devices' cells (0033, automated). Landed: `630be38`.
4. [x] **The QR display.** The shell encodes the payload into a ZXing-core matrix behind a seam
   (the SafSnapshotFiles pattern); `apps/todo/shared` adds a route from Settings that draws it on
   a Canvas. Strings English and German (0027). Record 0074 lands here, with the dependency.
   Landed: `3bcc9b6`.
5. [x] **The scan.** The shell scans via zxing-android-embedded behind the same seam; the join
   flow runs scan, parse, confirm (the data-shared copy), adopt, and states the group's transport.
   Device check: a phone scans the emulator's screen (or an emulator with the host's webcam as
   camera-back); the emulator's own join is exercised by the words path in slice 6. Code landed:
   `a6d3e70`; the on-device check is pending.
6. [ ] **The recovery screen and the clip.** A route showing the 24 words with copy (marked
   sensitive, cleared after 60 s, and the confirmation says so — 0041, shell code) and
   restore-from-words with honest errors. Emulator check: the Android 13+ preview shows dots, the
   clip clears, and a fresh install restored from words imports an old export.
7. [ ] **Two devices, one group.** Pair phone and emulator, then export/import both ways: both
   show the same lists, each device's pre-pairing data included, and a deletion crosses. Closes
   the step 6 two-device check and this plan; updates `product.md`, `architecture.md` → Identity
   and pairing, `v1-todo.md`, then deletes this plan (its lasting content moved to the docs).

## Open questions

None left: the QR content, the wordlist and the scanner were decided on 2026-10-18 — the QR
carries the backend descriptor (the type today, portable config when a backend has it), the
wordlist is English BIP39 in-repo, the scanner is zxing-android-embedded. Records 0072 to 0074
land with their slices.

## How to verify

`./gradlew allTests` green — the payload properties, the BIP39 vectors, the adopt flow, the
cross-replica merge. On devices: pair phone and emulator by QR, exchange the encrypted snapshot
both ways (the file that honestly failed at step 6 now imports), both devices keep their old
lists, a deletion crosses; a fresh install restored from words imports an old export; the copied
words show as dots and clear within 60 s. Live folder sync is step 8.
