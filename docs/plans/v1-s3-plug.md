# Plan: the S3 plug (v1 step 17)

Status: **slices 1 to 5 done (all code, green in `allTests`); slice 6, the device check against
real buckets, in progress: R2 checks 1 to 4, 8 and 16 passed on 2026-11-11 (see Results), B2 not
started.** Started 2026-11-10.

## Goal

A `BackendPlug` for S3-compatible object storage (AWS, Cloudflare R2, Backblaze B2, MinIO, Garage),
so a user can sync through a bucket they already pay for or get for free. Like every plug it only
moves ciphertext under the keys of decision 0029. Pairing carries the whole configuration, so a
second device syncs after one scan.

## Decisions

Decided 2026-11-07 (plan `v1-todo.md` step 17):

- SigV4 written on the Ktor client with cryptography-kotlin's HMAC/SHA-256; no SDK (decision 0084).
- The whole configuration, secret included, travels in the pairing QR (decision 0085).

Decided 2026-11-10 (described as built in `architecture.md` → S3; move what lasts into a record
when the plan closes):

- **Create-only:** `PUT` sends `If-None-Match: *`, and a 412 counts as success. If a provider
  refuses the header (B2 is reported to), the plug retries once without it and skips it for the
  rest of the sync session. Keys end in the hash of the ciphertext, so an overwrite writes the same
  bytes.
- **Fallback scope:** only a 501, or a 400 that maps to no refusal, triggers that retry; a wrong
  region (`WrongRegion`) or bad credentials are reported at once.
- **Listing:** `ListObjectsV2` with continuation tokens, parsed by a small hand-written extractor
  for `Key`, `IsTruncated` and `NextContinuationToken`; no XML dependency.
- **Layout:** an optional key prefix (for example `msuite/`) goes in front of the 0029 keys; it is
  part of the config and the QR.
- **Addressing:** virtual-hosted style for `amazonaws.com` endpoints, path style everywhere else.
- **Clock skew:** `RequestTimeTooSkewed` becomes a refusal shown in Settings, with no automatic
  offset.
- **QR section:** a kotlinx ProtoBuf message (decision 0085).
- **Secret at rest:** `KeyStoreSealedFile` holds the KeyStore wrapping for the root and the S3
  secret, one file each under the same key; the root's file format and key alias are unchanged.
  The secret is saved before S3 is chosen and deleted when another backend is chosen.
- `https://` only in release builds; debug builds also accept `http://` for a local MinIO, as for
  the relay (decision 0079).
- Payloads are signed (SHA-256 of the body), not `UNSIGNED-PAYLOAD`: batches are small.

Decided 2026-11-13:

- **Connect probes before saving** (relay and S3): list, write a probe object, delete it; only then
  save. Recorded as decision 0086.

## Slices

1. [x] **SigV4 signer** in a new KMP module `core/s3` (canonical request, string to sign, signing
   key, `Authorization` header). Test: AWS's SigV4 test vectors. Landed: `644c1ec` (decision 0084).
2. [x] **`S3Plug`** over a Ktor client the caller passes in: `put`/`get`/`delete`/`list` with
   pagination, the `If-None-Match` fallback, both addressing styles and the prefix; errors map to
   `S3Refused(reason)`. Tests: `PlugContract` and the error mapping against `FakeS3` on Ktor's
   `MockEngine`, and the listing parser on the AWS-documented shape. Landed: `289ecf3`, then
   `b6405b7` (`WrongRegion`, and known refusals kept out of the fallback).
3. [x] **Pairing section:** `BackendType.S3(3)` with its ProtoBuf section in `core/crypto`. Test:
   round trip, pinned field numbers, a malformed section rejected, a newer field skipped, and an
   older build still pairing from the root (the unknown-type test of 0072 covers it). Landed:
   `f77cbfb` (decision 0085).
4. [x] **Secret at rest:** `KeyStoreSealedFile` extracted from `KeyStoreRootStore`; the
   `SecretStore` seam in `apps/todo/domain`; `SyncBackend.S3` in `TodoSettings` without the secret.
   Test: `TodoSettingsTest` shows the stored keys hold no secret; the KeyStore side has no JVM test,
   as for the root (0039). Landed: `e9301c5`.
5. [x] **Settings and wiring:** S3 storage in the Sync method chooser with its form (`S3Details`,
   English and German), one message per refusal and for a lost secret; `LiveSyncActions.connectS3`,
   `needsS3Secret` and `pairingBackend`; joining from an S3 code and the switch offer for a code
   naming another bucket; `S3Section.toConfig()` as the one path from a QR to a config. The shell
   builds `S3Options` in `MainActivity` (not in `ShellServices`: the UI never builds an S3 plug)
   and shares one OkHttp client between the relay and S3 plugs. Tests: `S3SyncTest` (8),
   `PairingTest` (switch offer), `S3SetupTest`. Landed: `f60a7ea`.
6. [ ] **Device check** against real buckets, as in the test plan below. Then: record the results in
   `testing.md` → Two-device check (S3); add the captured responses as test cases; settle the open
   items below; move the lasting decisions above into `architecture.md` or a record; close step 17
   in `v1-todo.md` and its line in `roadmap.md`; delete this plan.

## What's open

Each item is settled during slice 6.

1. **B2's refusal of `If-None-Match`.** Only secondhand (Proxmox reports that B2 fails the request);
   the fallback therefore accepts any unexplained 400 or a 501. Capture B2's real status and error
   code, then narrow the fallback to it, with a test.
2. **Real responses as fixtures.** R2 done 2026-11-11 (`R2Responses` in `S3PlugTest`): a listing,
   `InvalidRegionName`, `AccessDenied`, `SignatureDoesNotMatch`, `Unauthorized` (401, unknown key),
   `NoSuchKey`. A token scoped to one bucket gets `403 AccessDenied` for a bucket that doesn't
   exist, never `NoSuchBucket`, so the Forbidden message now also names the bucket name. B2 open.
3. **R2's answer to a wrong region.** Settled 2026-11-11: R2 accepts `auto`, `us-east-1` and its
   location hints (`wnam`, `eeur`, …), and answers any other region `400 InvalidRegionName`, now
   mapped to `WrongRegion`.
4. **No S3-specific signature vector.** The signer passes AWS's generic suite; the S3 parts (the
   payload-hash header, single path encoding) are proven only by a real bucket accepting requests.
5. **The KeyStore refactor on an existing install.** It is designed to change nothing for the
   root; only an upgrade on a device proves that.
6. **QR size.** An R2 code is about 230 bytes. Check that it scans quickly from a phone screen at
   normal brightness.
7. **`needsS3Secret()` reads the KeyStore during composition** (Settings recomposes on every poll).
   Cheap per call, but measure; cache it in `LiveSyncActions` if Settings stutters.
8. **The licences line.** Settled 2026-11-13: it now names every non-AndroidX library in the
   release runtime classpath (Ktor, OkHttp, Okio, cryptography-kotlin, Kermit and kotlinx-io were
   missing), and SLF4J apart, as it is MIT, not Apache 2.0.
9. **Connect and Sync now** (relay and S3 forms; found 2026-11-11). Settled 2026-11-13: Connect
   probes before saving (decision 0086); listing in `4f916fb` (S3) and `df3bdc7` (relay), write and delete in
   `<hash>`. With that, "stored
   on this device" only appears for a working setup, and Connect fits a small edit too, so the
   labels stay.
10. **`\'` showed in English UI text.** Settled 2026-11-13: Compose resources don't unescape
    `\'`, so the 17 English strings now use a plain `'`, and `StringResourcesTest` (in
    `apps/todo/domain`, as `shared` has no tests) fails on any `\'`.

## Test plan (slice 6)

### Setup

- **Buckets.** Use the same check on two providers:
  - Cloudflare R2: a private bucket `msuite-check`. Create an API token with *Object Read & Write*
    limited to that bucket. Endpoint `https://<account-id>.r2.cloudflarestorage.com`, region
    `auto`.
  - Backblaze B2: a private bucket. Create an application key limited to it, with read, write,
    list and delete. Endpoint `https://s3.<region>.backblazeb2.com`, region as in the endpoint
    (for example `eu-central-003`).
  - AWS (optional, the only virtual-hosted path): a bucket in `eu-central-1` and an IAM user
    allowed `s3:PutObject`, `s3:GetObject`, `s3:DeleteObject` on `bucket/prefix/*` and
    `s3:ListBucket` on the bucket. Endpoint `https://s3.eu-central-1.amazonaws.com`.
- **Prefix** `todo-check`, so the objects sit under `todo-check/todo/{groupId}/`.
- **Inspecting the bucket** from a computer: the AWS CLI with `--endpoint-url`, for example
  `aws s3 ls --recursive s3://msuite-check/todo-check/ --endpoint-url https://<account-id>.r2.cloudflarestorage.com`.
  `aws s3api … --debug` prints raw responses for the fixtures.
- **Devices:** two phones with cameras (or a phone and an emulator, pairing the emulator with the
  recovery words and the S3 fields typed in). Install the same debug build on both; https
  endpoints, so the release rule is exercised too. Keep both apps in the foreground while syncing.
- **Database comparison** cell for cell as in the folder check; app files through
  `adb shell run-as com.msuite.todo`.

### Checks

| # | Check | How | Pass |
|---|---|---|---|
| 1 | Connect | device 1: S3 storage, fill the form, Connect | `Last sync` appears; the bucket lists objects under `todo-check/todo/{groupId}/` named per 0029 |
| 2 | Wrong secret | Connect with one character changed | "rejected the access key"; nothing written |
| 3 | Wrong bucket | a bucket name that doesn't exist | "The bucket doesn't exist" |
| 4 | Wrong region | R2 with `us-east-1`; AWS with `us-west-2` | AWS: the wrong-region message; R2: record the code (open item 3) |
| 5 | Too few rights | a read-only token | "may not use the bucket" |
| 6 | Clock off | turn automatic time off, set the clock 30 min ahead, Sync now | the clock message; after restoring the time it syncs |
| 7 | Bad endpoints | `http://…` in a release build; an endpoint with a path; a blank field | refused in the form with its message; the backend is unchanged |
| 8 | Join by QR | device 2 (other data, no backend) scans device 1's code | joins, S3 preselected with no typing, both converge; the code scans quickly (open item 6) |
| 9 | Switch offer | device 2 on a relay or another bucket scans a code of its own group | the dialog names the bucket; confirming switches and syncs |
| 10 | Edits | simultaneous, 1 / 5 / 10 s staggered, one device offline, a deletion crossing | both converge, nothing lost but the LWW loser |
| 11 | Soak | 10+ minutes of mixed edits on both | `field_state` identical cell for cell; `unreadable_batch` and `unconfirmed_publish` empty |
| 12 | Compaction | after many edits, count objects per writer | stays near `SNAPSHOT_EVERY` + 1 per writer |
| 13 | Prefix wiped | delete everything under the prefix | "looks empty"; after confirming, both refill and converge |
| 14 | Ciphertext only | download an object; `grep` it for a task title | no plaintext |
| 15 | Secret lifecycle | switch device 1 to Off, then look at `files/` and `shared_prefs/todo.xml` | `s3.secret` is gone; the prefs never held the secret, before or after |
| 16 | Keep or replace the secret | while connected, change the prefix and Connect with the secret empty; then Connect with a new secret | the empty field keeps the stored secret; a new one takes effect without a restart |
| 17 | Lost secret | delete `files/s3.secret` with `run-as`, force-stop, reopen | Settings says the secret is missing and sync stops; entering it resumes sync |
| 18 | Upgrade | install `7856935` (before the KeyStore refactor), create data and note the recovery words; install this build over it | same words, same data, sync continues (open item 5) |
| 19 | B2 fallback | repeat 1, 8, 10 and 13 on B2; separately run `aws s3api put-object --if-none-match '*'` against B2 | sync works; the CLI shows B2's status and code (open item 1) |
| 20 | Reboot and reinstall | reboot both; reinstall device 2 and pair it again by QR | both resume; device 2 rebuilds from the bucket alone |
| 21 | German | switch a device to German, walk the form and two errors | every S3 string in German, informal "du" |

Log signature: `LiveSync` warns `<operation> failed, retrying later` with the exception; an
`S3Refused` names the refusal and S3's error code, and Settings shows the matching message.

## Results

R2 (bucket `msuite-check`, prefix `todo-check`), 2026-11-11: emulator `Medium_Phone_API_36.1` as
device 1 (form filled by paste), Motorola edge 70 pro as device 2 (joined by scanning the code from
the monitor).

- **1 Connect:** passed; one object under `todo-check/todo/{groupId}/` named per 0029. The failed
  attempts before it wrote nothing.
- **2 Wrong secret:** passed. R2: `403 SignatureDoesNotMatch`; an unknown key ID `401 Unauthorized`.
- **3 Wrong bucket:** R2 answers a token scoped to one bucket with `403 AccessDenied`, so the app
  shows the Forbidden message, which now names the bucket name too (`b1717db`).
- **4 Wrong region:** R2 accepts `us-east-1` and its location hints; others get `400
  InvalidRegionName`, now `WrongRegion` (`b1717db`). The message shows only after the next sync,
  not on Connect (open item 9).
- **7 Bad endpoints:** a path and a blank field refused in the form; the `http://` release case is
  not run yet.
- **8 Join by QR:** passed: S3 preselected, nothing typed, both converged, quick scan.
- **16 Keep or replace the secret:** passed. The form now shows a stored secret as stored, with
  Change (`908bea4`).
- **18 Upgrade:** partly: device 2 had run a pre-refactor build since 2026-10-27 and kept its data
  through the 2026-11-10 upgrade; the words weren't noted, so rerun it on a fresh device.

Next session: redeploy the hosted relay (an older one refuses the probe, decision 0086); on R2
checks 5, 6, 7 (`http://` in a release build), 9 to 15, 17, 18, 20, 21; then
B2 (19); then settle open items 1, 2 (B2), 4 to 7.

## How to verify

The table above passes on R2 and on B2 (rows 1, 8, 10, 13 and 19 at least on B2), with every
open item settled. The bucket holds only ciphertext under the 0029 keys behind the prefix, and
`allTests` stays green with the new fixtures.
