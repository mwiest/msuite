# Plan: the S3 plug (v1 step 17)

Status: **slice 1 done; slice 2 (the plug) next.** Started 2026-11-10.

## Goal

A `BackendPlug` for S3-compatible object storage (AWS, Cloudflare R2, Backblaze B2, MinIO, Garage),
so a user can sync through a bucket they already pay for or get for free. Like every plug it only
moves ciphertext under the keys of decision 0029. Pairing carries the whole configuration, so a
second device syncs after one scan.

## Decisions

Decided 2026-11-07 (plan `v1-todo.md` step 17); each gets a record when its slice lands:

- SigV4 written on the Ktor client with cryptography-kotlin's HMAC/SHA-256; no SDK (decision 0084).
- The whole configuration, secret included, travels in the pairing QR, which already carries the
  root.

Decided 2026-11-10:

- **Create-only:** `PUT` sends `If-None-Match: *`, and a 412 counts as success. If a provider
  refuses the header as unsupported (B2 does), the plug retries once without it and remembers that
  for the session. Keys end in the hash of the ciphertext, so an overwrite writes the same bytes.
- **Listing:** `ListObjectsV2` with continuation tokens, parsed by a small hand-written extractor
  for `Key`, `IsTruncated` and `NextContinuationToken`; no XML dependency.
- **Layout:** an optional key prefix (for example `msuite/`) goes in front of the 0029 keys; it is
  part of the config and the QR.
- **Addressing:** virtual-hosted style for `amazonaws.com` endpoints, path style everywhere else.
- **Clock skew:** `RequestTimeTooSkewed` becomes a refusal shown in Settings ("check the device
  time"), with no automatic offset.
- **QR section:** a kotlinx ProtoBuf message (endpoint, region, bucket, prefix, access key ID,
  secret).
- **Secret at rest:** the KeyStore wrap/unwrap moves out of `KeyStoreRootStore` into a shared
  sealed-file helper; the root and the S3 secret are separate files under the same wrapping key. The
  root's file format stays.
- `https://` only in release builds; debug builds also accept `http://` for a local MinIO, as for
  the relay (decision 0079).
- Payloads are signed (SHA-256 of the body), not `UNSIGNED-PAYLOAD`: batches are small.

## Slices

1. [x] **SigV4 signer** in a new KMP module `core/s3` (canonical request, string to sign, signing
   key, `Authorization` header). Test: AWS's SigV4 test vectors. Landed: `644c1ec` (decision 0084).
2. [ ] **`S3Plug`** over a Ktor client the caller passes in: `put`/`get`/`delete`/`list` with
   pagination, the `If-None-Match` fallback, both addressing styles and the prefix; errors map to
   `S3Refused(reason)` (credentials, no bucket, clock skew, forbidden). Tests: `PlugContract` and
   the error mapping on Ktor's `MockEngine`, and the listing parser on the AWS-documented shape;
   real R2 and B2 responses are captured as test cases in slice 6.
3. [ ] **Pairing section:** `BackendType.S3(3)` with its ProtoBuf section in `core/crypto`. Test:
   round trip, a malformed section rejected, and an older build still pairing from the root.
4. [ ] **Secret at rest:** extract the sealed-file helper from `KeyStoreRootStore`; add a
   `SecretStore` seam in `apps/todo/domain`; `SyncBackend.S3` in `TodoSettings` without the secret.
   Test: domain tests with an in-memory store, showing settings never hold the secret.
5. [ ] **Settings and wiring:** the S3 form in `SyncBackendSection` (EN and DE), the plug factory in
   `ShellServices`, joining and the step 13 switch offer for S3 codes. Tests in `LiveSyncActions`
   and `Pairing`.
6. [ ] **Device check** on two phones against a real bucket (R2, plus B2 for the fallback). Then the
   decision records, `architecture.md` → Backend plugs, `product.md`, `testing.md`, and close step 17
   in `v1-todo.md`.

## Open questions

None open.

## How to verify

Two devices paired by QR sync through an R2 bucket: simultaneous and staggered writes, a deletion
crossing, reboot. The bucket holds only ciphertext under the 0029 keys (behind the prefix). The
same check passes on B2 through the `If-None-Match` fallback.
