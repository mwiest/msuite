# 0087: The S3 plug is create-only, path-style, and keeps its secret wrapped

Date: 2026-11-13 (decided 2026-11-10 in the S3 plug's plan, checked on R2 2026-11-11 to
2026-11-13). Status: accepted. Builds on 0003, 0029, 0084, 0085.

## Context

The S3 plug (v1 step 17) has to fit the plug contract (0003: append-only, create-only) on stores
that differ in small ways: AWS, Cloudflare R2, Backblaze B2, MinIO, Garage. It runs on Android
without an SDK (0084), and its secret is the first backend credential the app stores.

## Decision

- **Create-only:** `PUT` sends `If-None-Match: *`; a 412 counts as success. If a store refuses the
  header with a 501, or a 400 that maps to no known refusal (reported for B2), the put is sent
  again without it, and the plug skips the header for the rest of its lifetime. Keys end in the
  hash of the ciphertext (0029), so such an overwrite writes the same bytes. A wrong region or bad
  credentials never trigger that retry.
- **Listing:** `ListObjectsV2` across continuation tokens, read by a small extractor for `Key`,
  `IsTruncated` and `NextContinuationToken`; no XML dependency.
- **Layout:** an optional key prefix goes in front of the 0029 keys; it is part of the
  configuration and of the QR section (0085).
- **Addressing:** virtual-hosted style for `*.amazonaws.com`, path style for every other endpoint.
- **Refusals** map to `S3Refused` (bad credentials, forbidden, no such bucket, wrong region,
  clock skew), each with its own message in Settings. Clock skew is shown, not corrected: no
  automatic time offset.
- **Payloads are signed** (SHA-256 of the body in `x-amz-content-sha256`), not
  `UNSIGNED-PAYLOAD`.
- **Secret at rest:** the secret is wrapped under the same Android KeyStore key as the root, in a
  file of its own (`KeyStoreSealedFile`), never in the settings. It is saved before S3 is chosen
  and deleted when another backend is chosen. The root's file format and key alias are unchanged.
- **Transport:** `https://` only in release builds; debug builds also accept `http://` for a
  local MinIO, as for the relay (0079).

## Rejected

- Virtual-hosted style everywhere: MinIO and many self-hosted stores need path style, and R2
  accepts both.
- An XML library for the listing: three elements don't justify a dependency (or a JVM-only one).
- `UNSIGNED-PAYLOAD`: saves hashing the body, but batches are small, and a signed body is checked
  by the store.
- An automatic clock offset taken from the store's `Date` header: hides a device clock that is off
  and adds state; the message tells the user to fix the clock.
- The secret in the settings, or in the root's file: settings are plain preferences, and a
  separate file can be deleted on its own when the backend changes.

## Consequences

The plug works unchanged on R2 (checked on devices, `testing.md` → Two-device check (S3)). The
header fallback is broad until B2's real answer is captured; narrowing it then needs only a test
and the matching error code. A lost KeyStore key loses the secret: Settings asks for it again, or
a scan of the group's code restores it.
